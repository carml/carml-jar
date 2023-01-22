package io.carml.jar.runner.option;

import java.util.LinkedHashSet;
import java.util.Set;

public class OutputRdfFormats extends LinkedHashSet<String> {

  public OutputRdfFormats(Set<String> rdfFormats) {
    super(rdfFormats);
  }
}
