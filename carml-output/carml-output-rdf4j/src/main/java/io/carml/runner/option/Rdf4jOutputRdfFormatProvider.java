package io.carml.runner.option;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Rdf4jOutputRdfFormatProvider {

  @Bean
  public Set<String> rdfFormats() {
    return RDFWriterRegistry.getInstance()
        .getKeys()
        .stream()
        .map(RDFFormat::getDefaultFileExtension)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
