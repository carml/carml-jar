package io.carml.jar.runner.format;

import io.carml.jar.runner.CarmlJarException;
import lombok.NonNull;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;

public final class Rdf4JFormats {

  private Rdf4JFormats() {}

  public static RDFFormat determineRdfFormat(@NonNull String rdfFormat) {
    return RDFWriterRegistry.getInstance()
        .getKeys()
        .stream()
        .filter(format -> format.getDefaultFileExtension()
            .equals(rdfFormat))
        .findFirst()
        .orElseThrow(() -> new CarmlJarException(String.format("Could not determine RDFFormat for `%s`", rdfFormat)));
  }
}
