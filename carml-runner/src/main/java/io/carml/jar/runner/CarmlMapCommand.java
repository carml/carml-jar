package io.carml.jar.runner;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static picocli.CommandLine.ExitCode.OK;
import static picocli.CommandLine.ExitCode.USAGE;

import io.carml.engine.LoggingObserver;
import io.carml.engine.rdf.RdfRmlMapper;
import io.carml.engine.target.FileTargetWriter;
import io.carml.engine.target.StreamTargetWriter;
import io.carml.engine.target.TargetRouter;
import io.carml.engine.target.TargetWriter;
import io.carml.engine.target.TargetWriterFactory;
import io.carml.jar.runner.input.ModelLoader;
import io.carml.jar.runner.option.EvaluatorMode;
import io.carml.jar.runner.option.LoggingOptions;
import io.carml.jar.runner.option.MappingFileOptions;
import io.carml.jar.runner.option.OptionOrder;
import io.carml.jar.runner.option.OutputOptions;
import io.carml.jar.runner.output.OutputHandler;
import io.carml.jar.runner.prefix.NamespacePrefixMapper;
import io.carml.jar.runner.prefix.PrefixMappingException;
import io.carml.logicalview.DefaultLogicalViewEvaluatorFactory;
import io.carml.logicalview.duckdb.DuckDbLogicalViewEvaluatorFactory;
import io.carml.model.FilePath;
import io.carml.model.LogicalTarget;
import io.carml.model.Resource;
import io.carml.model.Target;
import io.carml.model.TriplesMap;
import io.carml.observability.MetricsObserver;
import io.carml.observability.PrometheusMetricsServer;
import io.carml.observability.PrometheusPushgateway;
import io.carml.output.SerializerMode;
import io.carml.util.ModelSerializer;
import io.carml.util.RmlMappingLoader;
import io.carml.util.RmlNamespaces;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.rio.RDFFormat;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Command(name = "map", sortOptions = false, sortSynopsis = false, mixinStandardHelpOptions = true,
    description = "Execute an RML mapping.")
public class CarmlMapCommand implements Callable<Integer> {

  private static final Logger LOG = LogManager.getLogger(CarmlMapCommand.class);

  private final ModelLoader modelLoader;

  private final OutputHandler outputHandler;

  private final NamespacePrefixMapper namespacePrefixMapper;

  private final List<RmlMapperConfigurer> rmlMapperConfigurers;

  private Map<String, String> namespaces;

  /**
   * Picocli mixin that adds the {@code -v}/{@code --verbose} CLI options. The field appears unused in
   * Java code because picocli binds it reflectively at parse time and
   * {@link LoggingOptions#executionStrategy} reads the verbosity state — suppressing the "unused
   * field" warning accordingly.
   */
  @SuppressWarnings("unused")
  @Mixin
  private LoggingOptions loggingOptions;

  @Mixin
  private MappingFileOptions mappingFileOptions;

  @Mixin
  private OutputOptions outputOptions;

  @Option(names = {"-M", "-pm", "--prefix-mapping"}, order = OptionOrder.PREFIX_MAPPING_ORDER,
      description = {"File or directory path(s) containing prefix mappings.",
          "Files must be JSON or YAML files containing a map of prefix declarations.",
          "File names must have either .json or .yaml/.yml file extensions."})
  private final List<Path> prefixMappings = new ArrayList<>();

  @Option(names = {"-p", "--prefixes"}, split = ",", order = OptionOrder.PREFIX_ORDER, description = {
      "Declares which prefixes to apply to the output.", "Can be a prefix reference or an inline prefix declaration.",
      "A prefix reference will be resolved against the provided prefix mapping (`-pm`), or the default prefix mapping.",
      "An inline prefix declaration can be provided as 'prefix=iri. For example: ex=http://example.com/'",
      "Multiple declarations can be separated by ','. For example: ex=http://example.com/,foo,bar"})
  private final List<String> prefixDeclarations = new ArrayList<>();

  @Option(names = {"-S", "--strict"}, order = OptionOrder.STRICT_ORDER, description = {"Enable strict mode.",
      "Raises an error if a reference expression never produces a value across all records of a logical source."})
  private boolean strict;

  @Option(names = {"-E", "--evaluator"}, defaultValue = "auto", order = OptionOrder.EVALUATOR_ORDER,
      converter = EvaluatorMode.Converter.class,
      description = {"Logical view evaluator mode.",
          "auto: Select best evaluator per view via ServiceLoader (default).",
          "reactive: Force reactive evaluator for all views.",
          "in-process-db: Force in-process database evaluator for all views."})
  private EvaluatorMode evaluatorMode;

  @Option(names = {"--spill-to-disk"}, order = OptionOrder.SPILL_TO_DISK_ORDER,
      description = {"Use an on-disk database instead of in-memory for the in-process-db evaluator.",
          "Enables processing of larger-than-memory datasets by spilling to disk.",
          "Memory and threads are auto-tuned. Minimum 512 MB system/container memory recommended.",
          "Only effective when evaluator mode is 'in-process-db' or 'auto'.",
          "In Docker, mount a volume to /duckdb-tmp for database and spill files:",
          "  docker run -v /tmp/duckdb:/duckdb-tmp carml map --spill-to-disk -m mapping.ttl"})
  private boolean spillToDisk;

  private static final java.util.regex.Pattern MEMORY_LIMIT_PATTERN = java.util.regex.Pattern
      .compile("^\\d+(\\.\\d+)?\\s*(B|KB|KiB|MB|MiB|GB|GiB|TB|TiB)$", java.util.regex.Pattern.CASE_INSENSITIVE);

  @Option(names = {"--in-process-db-memory"}, order = OptionOrder.DUCKDB_MEMORY_ORDER,
      description = {"Memory limit for the in-process database (e.g. '4GB', '512MB').",
          "Overrides the auto-tuned value. Only effective with --spill-to-disk.",
          "Default: system memory minus JVM heap minus 512 MB overhead."})
  private String inProcessDbMemory;

  @Option(names = {"--metrics"}, order = OptionOrder.METRICS_ORDER, arity = "0..1", fallbackValue = "localhost:9091",
      description = {"Push execution metrics to a Prometheus Pushgateway after mapping completes.",
          "Optionally specify host:port (default: localhost:9091).",
          "Also starts a Prometheus scrape endpoint on port 9092 for real-time monitoring.",
          "Metrics include statement counts, durations, iteration counts per TriplesMap.",
          "Start the monitoring stack with: docker compose -f docker/docker-compose.yml up -d"})
  private String metricsEndpoint;

  public CarmlMapCommand(ModelLoader modelLoader, OutputHandler outputHandler,
      NamespacePrefixMapper namespacePrefixMapper, List<RmlMapperConfigurer> rmlMapperConfigurers) {
    this.modelLoader = modelLoader;
    this.outputHandler = outputHandler;
    this.namespacePrefixMapper = namespacePrefixMapper;
    this.rmlMapperConfigurers = rmlMapperConfigurers;
  }

  @Override
  public Integer call() {
    long startNanos = System.nanoTime();

    try {
      namespaces = namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, prefixMappings);
    } catch (PrefixMappingException prefixMappingException) {
      LOG.error("{}", prefixMappingException.getMessage(), prefixMappingException);
      return USAGE;
    }

    if (spillToDisk && evaluatorMode == EvaluatorMode.reactive) {
      LOG.warn("--spill-to-disk is ignored when evaluator mode is 'reactive'");
    }
    if (inProcessDbMemory != null && !spillToDisk) {
      LOG.warn("--in-process-db-memory is ignored when --spill-to-disk is not set");
    }
    PrometheusMeterRegistry metricsRegistry = null;
    MetricsObserver metricsObserver = null;
    PrometheusMetricsServer metricsServer = null;
    if (metricsEndpoint != null) {
      metricsRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
      metricsObserver = MetricsObserver.create(metricsRegistry);
      try {
        metricsServer = PrometheusMetricsServer.start(metricsRegistry, 9092);
      } catch (IOException e) {
        LOG.warn("Could not start metrics server on port 9092: {}", e.getMessage());
      }
    }

    var duckDbFactory = createDuckDbFactory();

    try (duckDbFactory) {
      var mapping = loadMapping();
      var logicalTargets = collectLogicalTargets(mapping);

      if (logicalTargets.isEmpty()) {
        var rmlMapper = prepareMapper(mapping, duckDbFactory, metricsObserver, null);
        long nrOfStatements = useBytesPipeline() ? handleByteOutput(rmlMapper) : handleOutput(map(rmlMapper));
        LOG.info("Generated {} statements.", nrOfStatements);
      } else {
        // Observed- and written- counts are logged inside runRoutedMapping where the router state
        // is in scope — the distinction matters because mergeable statements (rdf:List /
        // rdf:Container) are counted as observed but bypass the observer chain and therefore do
        // not reach target writers.
        runRoutedMapping(mapping, logicalTargets, duckDbFactory, metricsObserver);
      }

      LOG.info("Finished processing.");
      long elapsedNanos = System.nanoTime() - startNanos;
      double elapsedSeconds = TimeUnit.NANOSECONDS.toMillis(elapsedNanos) / 1000.0;
      LOG.info("Processing took: {} seconds", elapsedSeconds);

      if (metricsRegistry != null) {
        var labels = new LinkedHashMap<String, String>();
        labels.put("evaluator", evaluatorMode.name());
        mappingFileOptions.getGroup()
            .getMappingFiles()
            .stream()
            .findFirst()
            .ifPresent(p -> labels.put("mapping", p.getFileName()
                .toString()));
        PrometheusPushgateway.push(metricsRegistry, metricsEndpoint, "carml", labels);
      }

      return OK;
    } finally {
      if (metricsServer != null) {
        metricsServer.stop();
      }
    }
  }

  private DuckDbLogicalViewEvaluatorFactory createDuckDbFactory() {
    if (evaluatorMode == EvaluatorMode.reactive) {
      return null;
    }
    if (inProcessDbMemory != null && !MEMORY_LIMIT_PATTERN.matcher(inProcessDbMemory)
        .matches()) {
      throw new CarmlJarException(
          "Invalid --in-process-db-memory value: '%s'. Expected format: <number><unit> (e.g. '4GB', '512MB')"
              .formatted(inProcessDbMemory));
    }
    var virtualThreadScheduler =
        Schedulers.fromExecutorService(Executors.newVirtualThreadPerTaskExecutor(), "duckdb-vt");
    return spillToDisk ? DuckDbLogicalViewEvaluatorFactory.createOnDisk(virtualThreadScheduler, inProcessDbMemory)
        : new DuckDbLogicalViewEvaluatorFactory(virtualThreadScheduler);
  }

  private RdfRmlMapper prepareMapper(Set<TriplesMap> mapping, DuckDbLogicalViewEvaluatorFactory duckDbFactory,
      MetricsObserver metricsObserver, TargetRouter targetRouter) {

    if (LOG.isDebugEnabled()) {
      var mappingModel = mapping.stream()
          .map(Resource::asRdf)
          .flatMap(Model::stream)
          .collect(ModelCollector.toModel());

      RmlNamespaces.applyRmlNameSpaces(mappingModel);
      namespaces.forEach(mappingModel::setNamespace);

      LOG.debug("The following mapping constructs were detected:");
      LOG.debug("{}{}", System.lineSeparator(),
          ModelSerializer.serializeAsRdf(mappingModel, RDFFormat.TURTLE, ModelSerializer.SIMPLE_WRITER_CONFIG, n -> n));
    }

    var mapperBuilder = RdfRmlMapper.builder()
        .triplesMaps(mapping)
        .strictMode(strict);

    var relativeSourceLocation = mappingFileOptions.getGroup()
        .getRelativeSourceLocation();

    relativeSourceLocation.ifPresent(location -> {
      LOG.debug("Setting relative source location {} ...", () -> location);
      mapperBuilder.fileResolver(location);
    });

    outputOptions.getBaseIri()
        .ifPresent(mapperBuilder::baseIri);

    if (evaluatorMode == EvaluatorMode.reactive) {
      mapperBuilder.logicalViewEvaluatorFactory(new DefaultLogicalViewEvaluatorFactory());
    } else if (duckDbFactory != null) {
      mapperBuilder.logicalViewEvaluatorFactory(duckDbFactory);
      if (evaluatorMode == EvaluatorMode.auto) {
        mapperBuilder.logicalViewEvaluatorFactory(new DefaultLogicalViewEvaluatorFactory());
      }
    }

    mapperBuilder.observer(LoggingObserver.create());
    if (metricsObserver != null) {
      mapperBuilder.observer(metricsObserver);
    }

    if (targetRouter != null) {
      mapperBuilder.targetRouter(targetRouter);
    }

    rmlMapperConfigurers.forEach(rmlMapperConfigurer -> rmlMapperConfigurer.configureMapper(mapperBuilder));

    return mapperBuilder.build();
  }

  private Set<TriplesMap> loadMapping() {
    List<Path> paths = mappingFileOptions.getGroup()
        .getMappingFiles();

    LOG.info("Loading mapping from paths {} ...", () -> paths);

    var mappingFormat = mappingFileOptions.getGroup()
        .getMappingFileRdfFormat();

    var mappingModel = modelLoader.loadModel(paths, mappingFormat);

    return RmlMappingLoader.build()
        .load(mappingModel);
  }

  private boolean useBytesPipeline() {
    var format = outputOptions.getOutputRdfFormat();
    return OutputHandler.BYTE_STREAMING_FORMATS.contains(format) && !outputOptions.isPretty();
  }

  private long handleByteOutput(RdfRmlMapper rmlMapper) {
    var format = outputOptions.getOutputRdfFormat();
    var bytes = "nq".equals(format) ? rmlMapper.mapToNQuadsBytes() : rmlMapper.mapToNTriplesBytes();

    var limitedBytes = outputOptions.getLimit()
        .map(bytes::take)
        .orElse(bytes);

    return outputOptions.getOutputPath()
        .map(outputPath -> outputBytesWithPath(outputPath, limitedBytes))
        .orElseGet(() -> outputBytesWithoutPath(limitedBytes));
  }

  private static Path resolveOutputPath(Path outputPath) {
    if (Files.isDirectory(outputPath)) {
      outputPath = outputPath.resolve("output");
    }
    try {
      Files.createDirectories(outputPath.getParent());
    } catch (IOException e) {
      throw new CarmlJarException("Error creating directory %s".formatted(outputPath), e);
    }
    return outputPath;
  }

  private long outputBytesWithPath(Path outputPath, Flux<byte[]> bytes) {
    var resolvedPath = resolveOutputPath(outputPath);
    logWritingOutputTo(resolvedPath);
    try (var outputStream = new BufferedOutputStream(Files.newOutputStream(resolvedPath))) {
      return outputHandler.outputStreamingBytes(bytes, outputStream);
    } catch (IOException e) {
      throw new CarmlJarException("Error writing to output path %s".formatted(resolvedPath), e);
    }
  }

  @SuppressWarnings("java:S106")
  private long outputBytesWithoutPath(Flux<byte[]> bytes) {
    logConsoleFallback();
    return outputHandler.outputStreamingBytes(bytes, System.out);
  }

  private Flux<Statement> map(RdfRmlMapper rmlMapper) {
    return rmlMapper.map(System.in);
  }

  private long handleOutput(Flux<Statement> statements) {
    var rdfFormat = outputOptions.getOutputRdfFormat();
    var pretty = outputOptions.isPretty();

    var outputStatements = outputOptions.getLimit()
        .map(statements::take)
        .orElse(statements);

    return outputOptions.getOutputPath()
        .map(outputPath -> outputWithPath(outputPath, outputStatements, rdfFormat, pretty))
        .orElseGet(() -> outputWithoutPath(outputStatements, rdfFormat, pretty));
  }

  private long outputWithPath(Path outputPath, Flux<Statement> statements, String rdfFormat, boolean pretty) {
    var resolvedPath = resolveOutputPath(outputPath);
    logWritingOutputTo(resolvedPath);
    try (var outputStream = new BufferedOutputStream(Files.newOutputStream(resolvedPath))) {
      return outputRdf(statements, rdfFormat, namespaces, outputStream, pretty);
    } catch (IOException ioException) {
      throw new CarmlJarException("Error writing to output path %s".formatted(resolvedPath), ioException);
    }
  }

  @SuppressWarnings("java:S106")
  private long outputWithoutPath(Flux<Statement> statements, String rdfFormat, boolean pretty) {
    logConsoleFallback();
    return outputRdf(statements, rdfFormat, namespaces, System.out, pretty);
  }

  private static void logWritingOutputTo(Path resolvedPath) {
    LOG.info("Writing output to {} ...", resolvedPath);
  }

  private static void logConsoleFallback() {
    LOG.info("No output file specified. Outputting to console ...{}", System::lineSeparator);
  }

  private long outputRdf(Flux<Statement> statements, String rdfFormat, Map<String, String> namespaces,
      OutputStream outputStream, boolean pretty) {
    if (outputHandler.isFormatStreamable(rdfFormat, pretty)) {
      return outputHandler.outputStreaming(statements.publishOn(Schedulers.boundedElastic()), rdfFormat, namespaces,
          outputStream);
    } else {
      return outputHandler.outputPretty(statements, rdfFormat, namespaces, outputStream);
    }
  }

  /**
   * Collects the union of all {@link LogicalTarget}s declared on any term map in the given
   * {@link TriplesMap}s. Delegates per-TriplesMap traversal to
   * {@link TriplesMap#getAllLogicalTargets()} and unions the results. Returns an empty set if no
   * logical targets are declared anywhere in the mapping — the CLI falls back to the existing
   * unrouted output path in that case.
   *
   * <p>
   * Visibility: package-private for direct unit testing against in-memory CARML models (see
   * {@code CarmlMapCommandTest}).
   */
  static Set<LogicalTarget> collectLogicalTargets(Set<TriplesMap> mapping) {
    return mapping.stream()
        .map(TriplesMap::getAllLogicalTargets)
        .flatMap(Set::stream)
        .collect(toUnmodifiableSet());
  }

  /**
   * Executes the mapping with an active {@link TargetRouter}. Statements are routed by the observer
   * chain to file targets per declared {@code rml:logicalTarget}, and unrouted statements go to the
   * CLI default sink (stdout or the {@code -o} file) via a {@link StreamTargetWriter} pointing at the
   * caller-owned output stream.
   *
   * <p>
   * Byte-streaming mode is bypassed when routing is active — the byte-level fast path does not thread
   * statements through the observer chain, so falling back to the object pipeline is the only way to
   * preserve routing semantics. This is a documented trade-off per Task 7.11.
   *
   * <p>
   * The routed pipeline logs both the <em>observed</em> count (statements seen by the CLI subscriber,
   * including mergeable results such as rdf:List / rdf:Container that currently skip the observer
   * chain) and the <em>written</em> count reported by the router. The two differ whenever a mapping
   * produces mergeable statements — writer output is strictly a subset of the observed count.
   */
  private void runRoutedMapping(Set<TriplesMap> mapping, Set<LogicalTarget> logicalTargets,
      DuckDbLogicalViewEvaluatorFactory duckDbFactory, MetricsObserver metricsObserver) {

    if (useBytesPipeline()) {
      LOG.warn("Byte-streaming mode (nt/nq raw output) is incompatible with target routing."
          + " Falling back to the object-statement pipeline. To re-enable byte streaming:"
          + " either remove rml:logicalTarget declarations from the mapping,"
          + " or choose a different output format.");
    }

    var resolvedOutputPath = outputOptions.getOutputPath()
        .map(CarmlMapCommand::resolveOutputPath);
    resolvedOutputPath.ifPresentOrElse(
        path -> LOG.info("Writing unrouted output to {} (declared targets: {}) ...", path, logicalTargets.size()),
        () -> LOG.info("No output file specified. Writing unrouted output to console ..."));

    try (var rawOutput = resolvedOutputPath.isPresent() ? openFileSink(resolvedOutputPath.get()) : openConsoleSink();
        var defaultWriter = StreamTargetWriter.builder()
            .outputStream(rawOutput)
            .format(outputOptions.getOutputRdfFormat())
            .mode(outputOptions.isPretty() ? SerializerMode.PRETTY : SerializerMode.STREAMING)
            .namespaces(namespaces)
            .build();
        var router = buildTargetRouter(logicalTargets, buildTargetWriterFactory(), defaultWriter)) {

      var rmlMapper = prepareMapper(mapping, duckDbFactory, metricsObserver, router);
      long observedCount = subscribeRoutedFlux(rmlMapper);
      LOG.info("Observed {} statements, wrote {} statements to {} target writers.", observedCount,
          router.getWrittenCount(), router.getTargetWriterCount());
    } catch (IOException ioException) {
      throw new CarmlJarException("Error opening output stream for routed mapping", ioException);
    }
  }

  /**
   * Opens a buffered file {@link OutputStream} at the given resolved path. The returned stream is
   * caller-owned and MUST be closed (use try-with-resources).
   */
  private static OutputStream openFileSink(Path resolvedOutputPath) throws IOException {
    return new BufferedOutputStream(Files.newOutputStream(resolvedOutputPath));
  }

  /**
   * Wraps {@code System.out} in a {@link CloseShieldOutputStream} so try-with-resources cleanup does
   * not close stdout. The returned stream is safe to close from a try-with-resources block.
   */
  @SuppressWarnings("java:S106") // stdout use is intentional for the console fallback.
  private static OutputStream openConsoleSink() {
    return CloseShieldOutputStream.wrap(System.out);
  }

  private TargetWriterFactory buildTargetWriterFactory() {
    var builder = TargetWriterFactory.builder();
    firstMappingDirectoryPath().ifPresent(builder::mappingDirectoryPath);
    return builder.build();
  }

  /**
   * Constructs a {@link TargetRouter} from the declared {@link LogicalTarget}s. File-based targets
   * ({@link FilePath}) are bound to {@link FileTargetWriter} instances via
   * {@link TargetWriterFactory}. Non-file target types are rejected with
   * {@link UnsupportedOperationException} because routing to SPARQL endpoints or other sinks is out
   * of scope for Task 7.11.
   *
   * <p>
   * Visibility: package-private to permit direct unit testing of the routing-construction logic
   * without spinning up a full mapping pipeline (see {@code CarmlMapCommandTest}).
   */
  static TargetRouter buildTargetRouter(Set<LogicalTarget> logicalTargets, TargetWriterFactory targetWriterFactory,
      TargetWriter defaultWriter) {
    var writers = new HashMap<LogicalTarget, TargetWriter>();
    for (var logicalTarget : logicalTargets) {
      Target target = logicalTarget.getTarget();
      if (target instanceof FilePath filePath) {
        writers.put(logicalTarget, targetWriterFactory.createFileWriter(filePath, target.getSerialization()));
      } else {
        // Concatenation must be wrapped before .formatted() — otherwise .formatted() applies only
        // to the last string literal and the placeholders in the earlier fragments are lost.
        var targetTypeName = target == null ? "null"
            : target.getClass()
                .getSimpleName();
        throw new UnsupportedOperationException(("Unsupported rml:target type for rml:logicalTarget <%s>: %s."
            + " Only file-based targets (rml:FilePath) are supported in this release — please file"
            + " an issue if you need another target type.").formatted(logicalTarget.getResourceName(), targetTypeName));
      }
    }
    return new TargetRouter(writers, defaultWriter);
  }

  /**
   * Subscribes the mapper's {@link Flux} of statements to drive the pipeline when a
   * {@link TargetRouter} is wired. The outer flux still emits every generated statement; we count via
   * {@code doOnNext} and discard the rest because the observer chain has already routed each
   * statement to the appropriate writer.
   *
   * <p>
   * Returns the <em>observed</em> count — the number of outer-flux emissions the CLI subscriber saw.
   * This includes mergeable statements (rdf:List / rdf:Container merge results) that are counted here
   * but intentionally skip the observer chain and are therefore NOT written to any target writer.
   * Writer output is reported separately via {@code TargetRouter.getWrittenCount()}.
   *
   * <p>
   * Note on {@code take(N)} + routing: with routing active, in-flight inner emissions may write up to
   * N+k statements to target writers before the cancel signal from {@code take} propagates upstream.
   * The observed count respects {@code take}'s semantics (stops at N), but writers may hold slightly
   * more. This is intrinsic to Reactor's async cancellation, not a bug.
   */
  private long subscribeRoutedFlux(RdfRmlMapper rmlMapper) {
    var statements = rmlMapper.map(System.in);
    var limited = outputOptions.getLimit()
        .map(statements::take)
        .orElse(statements);

    var observedCount = new AtomicLong();
    // publishOn shifts the observedCount++ and blockLast to boundedElastic;
    // observer firing (and writer.write()) already happened upstream on the engine's scheduler.
    limited.publishOn(Schedulers.boundedElastic())
        .doOnNext(statement -> observedCount.incrementAndGet())
        .blockLast();
    return observedCount.get();
  }

  private Optional<Path> firstMappingDirectoryPath() {
    return mappingFileOptions.getGroup()
        .getMappingFiles()
        .stream()
        .findFirst()
        .map(Path::toAbsolutePath)
        .map(Path::getParent);
  }
}
