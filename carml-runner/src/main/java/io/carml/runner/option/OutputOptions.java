package io.carml.runner.option;

import static io.carml.runner.option.OptionOrder.OUTFORMAT_ORDER;
import static io.carml.runner.option.OptionOrder.OUTPUT_ORDER;

import io.carml.runner.RdfFormat;
import java.nio.file.Path;
import lombok.Getter;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Getter
@Command
public class OutputOptions {

  @ArgGroup(exclusive = false, order = OUTPUT_ORDER)
  private Group group;

  @Getter
  static class Group {
    @Option(names = {"-o", "--output"}, order = OUTPUT_ORDER, description = { //
        "Output file path.", //
        "If path is directory, will default to fileName `output`.", //
        "If left empty will output to console."})
    private Path outputPath;

    @Option(names = {"-t", "-of", "--outformat"}, order = OUTFORMAT_ORDER, description = "Output RDF format (see -f).")
    private RdfFormat outputRdfFormat;
  }
}
