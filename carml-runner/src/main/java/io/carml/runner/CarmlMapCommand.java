package io.carml.runner;

import io.carml.engine.rdf.RdfRmlMapper;
import io.carml.logicalsourceresolver.CsvResolver;
import io.carml.logicalsourceresolver.JsonPathResolver;
import io.carml.logicalsourceresolver.XPathResolver;
import io.carml.model.Resource;
import io.carml.model.TriplesMap;
import io.carml.runner.option.MappingFileOptions;
import io.carml.runner.option.OutputOptions;
import io.carml.util.ModelSerializer;
import io.carml.util.Models;
import io.carml.util.RmlMappingLoader;
import io.carml.util.RmlNamespaces;
import io.carml.vocab.Rdf;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.eclipse.rdf4j.rio.Rio;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import reactor.core.publisher.Flux;

@Slf4j
@Command(name = "map", sortOptions = false, mixinStandardHelpOptions = true)
public class CarmlMapCommand implements Callable<Integer> {

  @Mixin
  private MappingFileOptions mappingFileOptions;

  @Mixin
  private OutputOptions outputOptions;

  @Override
  public Integer call() {
    System.out.println("bla");

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

    var specifiedRdfFormat = mappingFormat != null ? determineRdfFormat(mappingFormat.name()) : null;

    var mappingModel = paths.stream()
        .flatMap(path -> resolvePaths(List.of(path)).stream())
        .flatMap(path -> parsePathToStatements(path, specifiedRdfFormat))
        .collect(new ModelCollector());

    return RmlMappingLoader.build()
        .load(mappingModel);
  }

  private Stream<Statement> parsePathToStatements(Path path, RDFFormat specifiedRdfFormat) {
    try (var is = Files.newInputStream(path)) {
      var fileName = path.getFileName()
          .toString();

      RDFFormat rdfFormat;
      if (specifiedRdfFormat != null) {
        rdfFormat = specifiedRdfFormat;
      } else {
        rdfFormat = Rio.getParserFormatForFileName(path.getFileName()
            .toString())
            .orElseThrow(() -> new CarmlJarException(
                String.format("Could not determine mapping format for filename '%s'", fileName)));
      }

      return Models.parse(is, rdfFormat)
          .stream();
    } catch (IOException exception) {
      throw new CarmlJarException(String.format("Could not read file %s", path), exception);
    }
  }

  private List<Path> resolvePaths(List<Path> paths) {
    return paths.stream()
        .flatMap(path -> {
          try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile)
                .collect(Collectors.toList())
                .stream();
          } catch (IOException exception) {
            throw new CarmlJarException(String.format("Exception occurred while reading file %s", path), exception);
          }
        })
        .collect(Collectors.toList());
  }

  private RDFFormat determineRdfFormat(String format) {
    Objects.requireNonNull(format);

    return RDFWriterRegistry.getInstance()
        .getKeys()
        .stream()
        .filter(f -> f.getDefaultFileExtension()
            .equals(format))
        .findFirst()
        .orElseThrow(() -> new CarmlJarException(String.format("Could not determine RDFFormat for `%s`", format)));
  }

  private Flux<Statement> map(RdfRmlMapper rmlMapper) {
    return rmlMapper.map();
  }

  private long handleOutput(Flux<Statement> statements) {
    statements.collect(Collectors.toList())
        .block()
        .forEach(System.out::println);

    return -1;
  }
}
