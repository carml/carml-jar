package io.carml.jar.runner.prefix;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrefixMappingConfiguration {

  @Bean
  public YAMLMapper yamlMapper() {
    return new YAMLMapper();
  }
}
