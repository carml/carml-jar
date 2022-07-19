package io.carml.runner;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "carml", mixinStandardHelpOptions = true, sortOptions = false)
public class CarmlCommand {

}
