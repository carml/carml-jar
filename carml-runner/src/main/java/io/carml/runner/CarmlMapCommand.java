package io.carml.runner;

import static io.carml.runner.model.RdfFormat.n3;
import static io.carml.runner.model.RdfFormat.nq;
import static io.carml.runner.model.RdfFormat.nt;
import static io.carml.runner.model.RdfFormat.trig;
import static io.carml.runner.model.RdfFormat.trix;
import static io.carml.runner.model.RdfFormat.ttl;

import io.carml.engine.rdf.RdfRmlMapper;
import io.carml.logicalsourceresolver.CsvResolver;
import io.carml.logicalsourceresolver.JsonPathResolver;
import io.carml.logicalsourceresolver.XPathResolver;
import io.carml.model.Resource;
import io.carml.model.TriplesMap;
import io.carml.runner.input.ModelLoader;
import io.carml.runner.model.RdfFormat;
import io.carml.runner.option.MappingFileOptions;
import io.carml.runner.option.OutputOptions;
import io.carml.runner.output.OutputHandler;
import io.carml.util.ModelSerializer;
import io.carml.util.RmlMappingLoader;
import io.carml.util.RmlNamespaces;
import io.carml.vocab.Rdf;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@Command(name = "map", sortOptions = false, mixinStandardHelpOptions = true)
public class CarmlMapCommand implements Callable<Integer> {
  private static final Set<RdfFormat> STREAMING_FORMAT = Set.of(nt, nq);

  private static final Set<RdfFormat> POTENTIALLY_STREAMING_FORMAT = Set.of(ttl, trig, n3, trix);

  private final ModelLoader modelLoader;

  private final OutputHandler outputHandler;

  @Mixin
  private MappingFileOptions mappingFileOptions;

  @Mixin
  private OutputOptions outputOptions;

  public CarmlMapCommand(ModelLoader modelLoader, OutputHandler outputHandler) {
    this.modelLoader = modelLoader;
    this.outputHandler = outputHandler;
  }

  @Override
  public Integer call() {
    var rmlMapper = prepareMapper();
    var statements = map(rmlMapper);
    var nrOfStatements = handleOutput(statements);

    LOG.info("Finished processing.");
    LOG.info("Generated {} statements.", nrOfStatements);

    return 0;
  }

  private RdfRmlMapper prepareMapper() {
    var mapping = loadMapping();

    if (LOG.isDebugEnabled()) {
      var mappingModel = mapping.stream()
          .map(Resource::asRdf)
          .flatMap(Model::stream)
          .collect(ModelCollector.toModel());

      RmlNamespaces.applyRmlNameSpaces(mappingModel);
      // getOutputNamespaceDeclarations(cmd).forEach(mappingModel::setNamespace);

      LOG.debug("The following mapping constructs were detected:");
      LOG.debug("{}",
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
      mapperBuilder.fileResolver(relativeSourceLocation);
    }

    return mapperBuilder.build();
  }

  private Set<TriplesMap> loadMapping() {
    List<Path> paths = mappingFileOptions.getGroup()
        .getMappingFiles();

    LOG.info("Loading mapping from paths {} ...", paths);

    var mappingFormat = mappingFileOptions.getGroup()
        .getMappingFileRdfFormat();

    var mappingModel = modelLoader.loadModel(paths, mappingFormat);

    return RmlMappingLoader.build()
        .load(mappingModel);
  }

  public static RDFFormat determineRdfFormat(@NonNull String rdfFormat) {
    return RDFWriterRegistry.getInstance()
        .getKeys()
        .stream()
        .filter(format -> format.getDefaultFileExtension()
            .equals(rdfFormat))
        .findFirst()
        .orElseThrow(() -> new CarmlJarException(String.format("Could not determine RDFFormat for `%s`", rdfFormat)));
  }

  private Flux<Statement> map(RdfRmlMapper rmlMapper) {
    return rmlMapper.map();
  }

  private long handleOutput(Flux<Statement> statements) {
    var outputPath = outputOptions.getGroup()
        .getOutputPath();

    var rdfFormat = outputOptions.getGroup()
        .getOutputRdfFormat();

    if (outputPath == null) {
      LOG.info("No output file specified. Outputting to console...{}", System.lineSeparator());
      return outputRdf(statements, rdfFormat, Map.of(), System.out);
    } else {
      LOG.info("Writing output to {} ...", outputPath);
      try (var outputStream = new BufferedOutputStream(Files.newOutputStream(outputPath, StandardOpenOption.CREATE))) {
        return outputRdf(statements, rdfFormat, Map.of(), outputStream);
      } catch (IOException ioException) {
        throw new CarmlJarException(String.format("Error writing to output path %s", outputPath), ioException);
      }
    }
  }

  private long outputRdf(Flux<Statement> statements, RdfFormat format, Map<String, String> namespaces,
      OutputStream outputStream) {
    if (isOutputStreamable(format, outputOptions.getGroup()
        .isPretty())) {
      return outputHandler.outputStreaming(statements, format, namespaces, outputStream);
    } else {
      return outputHandler.outputPretty(statements, format, namespaces, outputStream);
    }
  }

  private boolean isOutputStreamable(RdfFormat format, boolean pretty) {
    return STREAMING_FORMAT.contains(format) || (!pretty && POTENTIALLY_STREAMING_FORMAT.contains(format));
  }
}
