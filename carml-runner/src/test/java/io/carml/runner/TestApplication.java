package io.carml.runner;

import io.carml.runner.format.RdfFormat;
import io.carml.runner.output.OutputHandler;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.eclipse.rdf4j.model.Statement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

@SpringBootApplication
public class TestApplication {

  public static void main(String... args) {
    System.exit(SpringApplication.exit(SpringApplication.run(TestApplication.class, args)));
  }

  public static Path getTestSourcePath(Path toResolve) {
    return Paths.get("src", "test", "resources", "io", "carml", "runner")
        .resolve(toResolve);
  }

  public static String getStringForPath(Path base, String first, String... more) {
    return base.resolve(Paths.get(first, more))
        .toString();
  }

  public static Level getLogLevel(String name) {
    LoggerContext loggerContext = LoggerContext.getContext(false);
    LoggerConfig loggerConfig = loggerContext.getConfiguration()
        .getLoggerConfig(name);

    return loggerConfig.getLevel();
  }

  @Bean
  public OutputHandler outputHandler() {
    return new OutputHandler() {
      @Override
      public long outputPretty(Flux<Statement> statementFlux, RdfFormat format, Map<String, String> namespaces,
          OutputStream outputStream) {
        return 1;
      }

      @Override
      public long outputStreaming(Flux<Statement> statementFlux, RdfFormat format, Map<String, String> namespaces,
          OutputStream outputStream) {
        return 2;
      }
    };
  }
}
