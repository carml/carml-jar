package io.carml.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public class CarmlRunner implements CommandLineRunner, ExitCodeGenerator {

  private final CarmlCommand carmlCommand;

  private int exitCode;

  public CarmlRunner(CarmlCommand carmlCommand) {
    this.carmlCommand = carmlCommand;
  }

  @Override
  public void run(String... args) {
    var commandLine = new CommandLine(carmlCommand);
    // commandLine.usage(System.out, CommandLine.Help.Ansi.ON);
    exitCode = commandLine.execute(args);
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
