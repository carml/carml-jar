package io.carml.runner.input;

import static io.carml.runner.CarmlMapCommand.determineRdfFormat;

import io.carml.runner.CarmlJarException;
import io.carml.runner.model.RdfFormat;
import io.carml.util.Models;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.springframework.stereotype.Component;

@Component
public class Rdf4jModelLoader implements ModelLoader {
  @Override
  public Model loadModel(List<Path> paths, RdfFormat rdfFormat) {
    var specifiedRdfFormat = rdfFormat != null ? determineRdfFormat(rdfFormat.name()) : null;

    return paths.stream()
        .flatMap(path -> PathResolver.resolvePaths(List.of(path))
            .stream())
        .flatMap(path -> parsePathToStatements(path, specifiedRdfFormat))
        .collect(new ModelCollector());
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

}
