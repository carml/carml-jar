package io.carml.jar.runner;

import io.carml.engine.rdf.RdfRmlMapper.Builder;
import org.springframework.stereotype.Component;

@Component
public class FunctionRmlMapperConfigurer implements RmlMapperConfigurer {

  @Override
  public void configureMapper(Builder builder) {
    builder.addFunctions(new GrelFunctions());
  }
}
