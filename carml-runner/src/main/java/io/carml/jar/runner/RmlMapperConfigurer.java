package io.carml.jar.runner;

import io.carml.engine.rdf.RdfRmlMapper;

public interface RmlMapperConfigurer {

  void configureMapper(RdfRmlMapper.Builder builder);
}
