package io.carml.jar.runner;

import static picocli.CommandLine.ExitCode.OK;
import static picocli.CommandLine.ExitCode.USAGE;

import io.carml.engine.rdf.RdfRmlMapper;
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
import io.carml.model.Resource;
import io.carml.model.TriplesMap;
import io.carml.util.ModelSerializer;
import io.carml.util.RmlMappingLoader;
import io.carml.util.RmlNamespaces;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
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

@Command(name = "map", sortOptions = false, sortSynopsis = false, mixinStandardHelpOptions = true)
public class CarmlMapCommand implements Callable<Integer> {

  private static final Logger LOG = LogManager.getLogger(CarmlMapCommand.class);

  private final ModelLoader modelLoader;

  private final OutputHandler outputHandler;

  private final NamespacePrefixMapper namespacePrefixMapper;

  private final List<RmlMapperConfigurer> rmlMapperConfigurers;

  private Map<String, String> namespaces;

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
          "Only effective when evaluator mode is 'in-process-db' or 'auto'."})
  private boolean spillToDisk;

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

    var duckDbFactory = createDuckDbFactory();

    try (duckDbFactory) {
      var rmlMapper = prepareMapper(duckDbFactory);
      var statements = map(rmlMapper);
      var nrOfStatements = handleOutput(statements);

      long elapsedNanos = System.nanoTime() - startNanos;
      double elapsedSeconds = TimeUnit.NANOSECONDS.toMillis(elapsedNanos) / 1000.0;
      LOG.info("Finished processing.");
      LOG.info("Generated {} statements.", nrOfStatements);
      LOG.info("Processing took: {} seconds", elapsedSeconds);

      return OK;
    }
  }

  private DuckDbLogicalViewEvaluatorFactory createDuckDbFactory() {
    if (evaluatorMode != EvaluatorMode.reactive && (evaluatorMode == EvaluatorMode.in_process_db || spillToDisk)) {
      return spillToDisk ? DuckDbLogicalViewEvaluatorFactory.createOnDisk() : new DuckDbLogicalViewEvaluatorFactory();
    }
    return null;
  }

  private RdfRmlMapper prepareMapper(DuckDbLogicalViewEvaluatorFactory duckDbFactory) {
    var mapping = loadMapping();

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
    if (!Files.isDirectory(outputPath)) {
      try {
        Files.createDirectories(outputPath.getParent());
      } catch (IOException ioException) {
        throw new CarmlJarException("Error creating directory %s".formatted(outputPath), ioException);
      }
    } else {
      outputPath = outputPath.resolve("output");
    }
    LOG.info("Writing output to {} ...", outputPath);
    try (var outputStream = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
      return outputRdf(statements, rdfFormat, namespaces, outputStream, pretty);
    } catch (IOException ioException) {
      throw new CarmlJarException("Error writing to output path %s".formatted(outputPath), ioException);
    }
  }

  @SuppressWarnings("java:S106")
  private long outputWithoutPath(Flux<Statement> statements, String rdfFormat, boolean pretty) {
    LOG.info("No output file specified. Outputting to console ...{}", System::lineSeparator);
    return outputRdf(statements, rdfFormat, namespaces, System.out, pretty);
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
}
