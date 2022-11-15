package io.carml.runner;

import static io.carml.runner.option.OptionOrder.PREFIX_MAPPING_ORDER;
import static io.carml.runner.option.OptionOrder.PREFIX_ORDER;
import static picocli.CommandLine.ExitCode.OK;
import static picocli.CommandLine.ExitCode.USAGE;

import io.carml.engine.rdf.RdfRmlMapper;
import io.carml.logicalsourceresolver.CsvResolver;
import io.carml.logicalsourceresolver.JsonPathResolver;
import io.carml.logicalsourceresolver.XPathResolver;
import io.carml.model.Resource;
import io.carml.model.TriplesMap;
import io.carml.runner.input.ModelLoader;
import io.carml.runner.option.LoggingOptions;
import io.carml.runner.option.MappingFileOptions;
import io.carml.runner.option.OutputOptions;
import io.carml.runner.output.OutputHandler;
import io.carml.runner.prefix.NamespacePrefixMapper;
import io.carml.runner.prefix.PrefixMappingException;
import io.carml.util.ModelSerializer;
import io.carml.util.RmlMappingLoader;
import io.carml.util.RmlNamespaces;
import io.carml.vocab.Rdf;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Component
@Command(name = "map", sortOptions = false, mixinStandardHelpOptions = true)
public class CarmlMapCommand implements Callable<Integer> {

  private static final Logger LOG = LogManager.getLogger();

  private final ModelLoader modelLoader;

  private final OutputHandler outputHandler;

  private final NamespacePrefixMapper namespacePrefixMapper;

  private Map<String, String> namespaces;

  @Mixin
  private LoggingOptions loggingOptions;

  @Mixin
  private MappingFileOptions mappingFileOptions;

  @Mixin
  private OutputOptions outputOptions;

  @Option(names = {"-M", "-pm", "--prefix-mapping"}, order = PREFIX_MAPPING_ORDER,
      description = {"File or directory path(s) containing prefix mappings.",
          "Files must be JSON or YAML files containing a map of prefix declarations.",
          "File names must have either .json or .yaml/.yml file extensions."})
  private final List<Path> prefixMappings = new ArrayList<>();

  @Option(names = {"-p", "--prefixes"}, split = ",", order = PREFIX_ORDER, description = {
      "Declares which prefixes to apply to the output.", "Can be a prefix reference or an inline prefix declaration.",
      "A prefix reference will be resolved against the provided prefix mapping (`-pm`), or the default prefix mapping.",
      "An inline prefix declaration can be provided as 'prefix=iri. For example: ex=http://example.com/'",
      "Multiple declarations can be separated by ','. For example: ex=http://example.com/,foo,bar"})
  private final List<String> prefixDeclarations = new ArrayList<>();

  public CarmlMapCommand(ModelLoader modelLoader, OutputHandler outputHandler,
      NamespacePrefixMapper namespacePrefixMapper) {
    this.modelLoader = modelLoader;
    this.outputHandler = outputHandler;
    this.namespacePrefixMapper = namespacePrefixMapper;
  }

  @Override
  public Integer call() {
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    try {
      namespaces = namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, prefixMappings);
    } catch (PrefixMappingException prefixMappingException) {
      LOG.error("{}", prefixMappingException.getMessage(), prefixMappingException);
      return USAGE;
    }

    var rmlMapper = prepareMapper();
    var statements = map(rmlMapper);
    var nrOfStatements = handleOutput(statements);

    stopWatch.stop();
    LOG.info("Finished processing.");
    LOG.info("Generated {} statements.", nrOfStatements);
    LOG.info("Processing took: {} seconds,{}{}", stopWatch::getTotalTimeSeconds, System::lineSeparator,
        stopWatch::prettyPrint);

    return OK;
  }

  private RdfRmlMapper prepareMapper() {
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
        .setLogicalSourceResolver(Rdf.Ql.Csv, CsvResolver::getInstance)
        .setLogicalSourceResolver(Rdf.Ql.JsonPath, JsonPathResolver::getInstance)
        .setLogicalSourceResolver(Rdf.Ql.XPath, XPathResolver::getInstance)
        .triplesMaps(mapping);

    var relativeSourceLocation = mappingFileOptions.getGroup()
        .getRelativeSourceLocation();
    if (relativeSourceLocation != null) {
      LOG.debug("Setting relative source location {} ...", () -> relativeSourceLocation);
      mapperBuilder.fileResolver(relativeSourceLocation);
    }

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

  @SuppressWarnings("java:S106")
  private long handleOutput(Flux<Statement> statements) {
    var outputPath = outputOptions.getOutputPath();
    var rdfFormat = outputOptions.getOutputRdfFormat();
    var pretty = outputOptions.isPretty();

    if (outputPath == null) {
      LOG.info("No output file specified. Outputting to console ...{}", System::lineSeparator);
      return outputRdf(statements, rdfFormat, namespaces, System.out, pretty);
    } else {
      if (!Files.isDirectory(outputPath)) {
        try {
          Files.createDirectories(outputPath.getParent());
        } catch (IOException ioException) {
          throw new CarmlJarException(String.format("Error creating directory %s", outputPath), ioException);
        }
      } else {
        outputPath = outputPath.resolve("output");
      }
      LOG.info("Writing output to {} ...", outputPath);
      try (var outputStream = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
        return outputRdf(statements, rdfFormat, Map.of(), outputStream, pretty);
      } catch (IOException ioException) {
        throw new CarmlJarException(String.format("Error writing to output path %s", outputPath), ioException);
      }
    }
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
