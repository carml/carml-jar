package io.carml.jar.runner.option;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JenaOutputRdfFormatProvider {

  @Bean
  public Set<String> rdfFormats() {
    return RDFLanguages.getRegisteredLanguages()
        .stream()
        .map(Lang::getFileExtensions)
        .flatMap(List::stream)
        .sorted()
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
