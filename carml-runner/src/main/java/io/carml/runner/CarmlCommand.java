package io.carml.runner;

import io.carml.runner.option.LoggingOptions;
import lombok.Getter;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Component
@Command(name = "", mixinStandardHelpOptions = true, sortOptions = false)
public class CarmlCommand {

  @Getter
  @Mixin
  private LoggingOptions loggingOptions;
}
