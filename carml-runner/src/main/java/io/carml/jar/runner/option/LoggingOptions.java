package io.carml.jar.runner.option;

import static picocli.CommandLine.Spec.Target.MIXEE;

import io.carml.jar.runner.CarmlCommand;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

public class LoggingOptions {

  public static final String CARML_LOGGER = "io.carml";

  @Spec(MIXEE)
  CommandSpec targetCommand;

  boolean[] verbosity = new boolean[0];

  @Option(names = {"-v", "--verbose"}, order = OptionOrder.VERBOSITY_ORDER,
      description = {"Specify multiple -v or --verbose options to increase verbosity.",
          "For example `-v -v`, or `-vv` or `--verbose --verbose`"})
  public void setVerbosity(boolean[] verbosity) {
    getTopLevelCommandLoggingOptions(targetCommand).verbosity = verbosity;
  }

  public boolean[] getVerbosity() {
    return getTopLevelCommandLoggingOptions(targetCommand).verbosity;
  }

  private static LoggingOptions getTopLevelCommandLoggingOptions(CommandSpec commandSpec) {
    return ((CarmlCommand) commandSpec.root()
        .userObject()).getLoggingOptions();
  }

  public static int executionStrategy(ParseResult parseResult) {
    getTopLevelCommandLoggingOptions(parseResult.commandSpec()).configureLoggers();
    return new CommandLine.RunLast().execute(parseResult);
  }

  public void configureLoggers() {
    Level level = getTopLevelCommandLoggingOptions(targetCommand).calculateLogLevel();

    LoggerContext loggerContext = LoggerContext.getContext(false);
    LoggerConfig carmlLoggerConfig = loggerContext.getConfiguration()
        .getLoggerConfig(CARML_LOGGER);
    if (carmlLoggerConfig.getLevel()
        .isMoreSpecificThan(level)) {
      carmlLoggerConfig.setLevel(level);
    }
    loggerContext.updateLoggers();
  }

  private Level calculateLogLevel() {
    switch (getVerbosity().length) {
      case 0:
        return Level.ERROR;
      case 1:
        return Level.INFO;
      case 2:
        return Level.DEBUG;
      default:
        return Level.TRACE;
    }
  }
}
