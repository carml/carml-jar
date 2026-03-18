package io.carml.jar.runner.option;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;

public final class Rdf4jOutputRdfFormatProvider {

  private Rdf4jOutputRdfFormatProvider() {}

  public static Set<String> rdfFormats() {
    return RDFWriterRegistry.getInstance()
        .getKeys()
        .stream()
        .map(RDFFormat::getDefaultFileExtension)
        .sorted()
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
