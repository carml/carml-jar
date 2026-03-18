package io.carml.jar.runner.option;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;

public final class JenaOutputRdfFormatProvider {

  private JenaOutputRdfFormatProvider() {}

  public static Set<String> rdfFormats() {
    return RDFLanguages.getRegisteredLanguages()
        .stream()
        .map(Lang::getFileExtensions)
        .flatMap(List::stream)
        .sorted()
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
