package io.carml.jar.app;

import io.carml.jar.runner.CarmlRunner;
import io.carml.jar.runner.option.JenaOutputRdfFormatProvider;
import io.carml.jar.runner.output.JenaOutputHandler;

public class CarmlJarJenaApplication {

  public static void main(String... args) {
    System.setProperty("org.jooq.no-logo", "true");
    System.setProperty("org.jooq.no-tips", "true");
    var outputHandler = new JenaOutputHandler();
    var rdfFormats = JenaOutputRdfFormatProvider.rdfFormats();
    System.exit(CarmlRunner.execute(args, outputHandler, rdfFormats));
  }
}
