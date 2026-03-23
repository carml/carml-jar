package io.carml.jar.app;

import io.carml.jar.runner.CarmlRunner;
import io.carml.jar.runner.option.Rdf4jOutputRdfFormatProvider;
import io.carml.jar.runner.output.Rdf4jOutputHandler;

public class CarmlJarRdf4jApplication {

  public static void main(String... args) {
    System.setProperty("org.jooq.no-logo", "true");
    System.setProperty("org.jooq.no-tips", "true");
    var outputHandler = new Rdf4jOutputHandler();
    var rdfFormats = Rdf4jOutputRdfFormatProvider.rdfFormats();
    System.exit(CarmlRunner.execute(args, outputHandler, rdfFormats));
  }
}
