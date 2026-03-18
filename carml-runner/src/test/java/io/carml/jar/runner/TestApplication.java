package io.carml.jar.runner;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Test utility methods for CarmlRunner tests.
 */
public final class TestApplication {

  private TestApplication() {}

  public static Path getTestSourcePath(Path toResolve) {
    return Paths.get("src", "test", "resources", "io", "carml", "jar", "runner")
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
}
