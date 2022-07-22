package io.carml.runner.option;

import static io.carml.runner.option.OptionOrder.OUT_FORMAT_ORDER;
import static io.carml.runner.option.OptionOrder.OUTPUT_ORDER;
import static io.carml.runner.option.OptionOrder.PRETTY_ORDER;

import io.carml.runner.format.RdfFormat;
import java.nio.file.Path;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Getter
@Command
public class OutputOptions {

  @Option(names = {"-F", "-of", "--outformat"}, defaultValue = "nq", order = OUT_FORMAT_ORDER,
      description = "Output RDF format (see -f). Default: ${DEFAULT-VALUE}.")
  private RdfFormat outputRdfFormat;

  @Option(names = {"-o", "--output"}, order = OUTPUT_ORDER, description = { //
      "Output file path.", //
      "If path is directory, will default to fileName `output`.", //
      "If left empty will output to console."})
  private Path outputPath;

  @Option(names = {"-P", "--pretty"}, order = PRETTY_ORDER,
      description = "Serialize pretty printed output. (Caution: will cause in-memory output collection).")
  private boolean pretty;

}
