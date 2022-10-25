package io.carml.runner;

import static picocli.CommandLine.defaultFactory;

import io.carml.runner.option.OutputRdfFormats;
import java.util.Set;
import org.springframework.stereotype.Component;
import picocli.CommandLine.IFactory;

@Component
public class CarmlRunnerFactory implements IFactory {

  private final Set<String> rdfFormats;

  /**
   * Looks for a required {@code rdfFormats} bean to use for output format support.
   *
   * @param rdfFormats the RDF Format bean.
   */
  public CarmlRunnerFactory(Set<String> rdfFormats) {
    if (rdfFormats.isEmpty()) {
      throw new CarmlJarException(
          "INCORRECT APPLICATION CONFIGURATION: Missing required bean of type Set<String> providing supported RDF"
              + " output formats.");
    }
    this.rdfFormats = rdfFormats;
  }

  @Override
  public <K> K create(Class<K> cls) throws Exception {

    if (cls.isAssignableFrom(OutputRdfFormats.class)) {
      return cls.cast(new OutputRdfFormats(rdfFormats));
    }

    return defaultFactory().create(cls);
  }
}
