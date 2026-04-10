package io.carml.jar.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.carml.jar.runner.input.Rdf4jModelLoader;
import io.carml.jar.runner.option.LoggingOptions;
import io.carml.jar.runner.option.OutputRdfFormats;
import io.carml.jar.runner.output.OutputHandler;
import io.carml.jar.runner.prefix.DefaultNamespacePrefixMapper;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import picocli.CommandLine;

public final class CarmlRunner {

  private CarmlRunner() {}

  public static int execute(String[] args, OutputHandler outputHandler, Set<String> rdfFormats) {
    var modelLoader = new Rdf4jModelLoader();
    var prefixMapper = new DefaultNamespacePrefixMapper(new ObjectMapper(), new YAMLMapper());
    var configurers = loadConfigurers();
    var outputRdfFormats = new OutputRdfFormats(rdfFormats);
    var mapCommand = new CarmlMapCommand(modelLoader, outputHandler, prefixMapper, configurers);
    var planCommand = new CarmlPlanCommand(modelLoader);

    var commandLine = new CommandLine(new CarmlCommand(), createFactory(outputRdfFormats))
        .setExecutionStrategy(LoggingOptions::executionStrategy)
        .addSubcommand("map", mapCommand)
        .addSubcommand("plan", planCommand);

    return commandLine.execute(args);
  }

  private static List<RmlMapperConfigurer> loadConfigurers() {
    return ServiceLoader.load(RmlMapperConfigurer.class)
        .stream()
        .map(ServiceLoader.Provider::get)
        .toList();
  }

  private static CommandLine.IFactory createFactory(OutputRdfFormats outputRdfFormats) {
    return new CommandLine.IFactory() {
      @Override
      public <K> K create(Class<K> cls) throws Exception {
        if (cls.isAssignableFrom(OutputRdfFormats.class)) {
          return cls.cast(outputRdfFormats);
        }
        return CommandLine.defaultFactory()
            .create(cls);
      }
    };
  }
}
