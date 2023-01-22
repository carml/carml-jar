package io.carml.jar.runner;

import io.carml.jar.runner.option.LoggingOptions;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public class CarmlRunner implements CommandLineRunner, ExitCodeGenerator {

  private final CarmlRunnerFactory carmlRunnerFactory;

  private final CarmlCommand carmlCommand;

  private final CarmlMapCommand carmlMapCommand;

  private int exitCode;

  public CarmlRunner(CarmlRunnerFactory carmlRunnerFactory, CarmlCommand carmlCommand,
      CarmlMapCommand carmlMapCommand) {
    this.carmlRunnerFactory = carmlRunnerFactory;
    this.carmlCommand = carmlCommand;
    this.carmlMapCommand = carmlMapCommand;
  }

  @Override
  public void run(String... args) {
    var commandLine =
        new CommandLine(carmlCommand, carmlRunnerFactory).setExecutionStrategy(LoggingOptions::executionStrategy)
            .addSubcommand("map", carmlMapCommand);

    exitCode = commandLine.execute(args);
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
