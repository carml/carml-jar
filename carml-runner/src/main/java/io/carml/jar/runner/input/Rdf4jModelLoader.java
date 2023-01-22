package io.carml.jar.runner.input;

import static io.carml.jar.runner.format.Rdf4JFormats.determineRdfFormat;

import io.carml.jar.runner.CarmlJarException;
import io.carml.jar.runner.format.RdfFormat;
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
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.springframework.stereotype.Component;

@Component
public class Rdf4jModelLoader implements ModelLoader {

  /**
   * Load {@link Model} from list paths, using all file {@link Path}s in the file tree starting from
   * given {@link Path}.
   *
   * @param paths the {@link List} of {@link Path}s to search through and load from.
   * @param rdfFormat the RDF format of the files. if null, the file extensions will be used to
   *        attempt to determine the format of each file.
   * @return the {@link Model}.
   */
  @Override
  public Model loadModel(List<Path> paths, RdfFormat rdfFormat) {
    var specifiedRdfFormat = rdfFormat != null ? determineRdfFormat(rdfFormat.name()) : null;

    return paths.stream()
        .flatMap(path -> FilePathResolver.resolveFilePaths(List.of(path))
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
        rdfFormat = Rio.getParserFormatForFileName(fileName)
            .orElseThrow(() -> new CarmlJarException(
                String.format("Could not determine mapping format by file extension for path '%s'", path)));
      }
      return Models.parse(is, rdfFormat)
          .stream();
    } catch (IOException | RDFParseException exception) {
      throw new CarmlJarException(String.format("Exception occurred while parsing %s", path), exception);
    }
  }

}
