package io.carml.runner.option;

import static io.carml.runner.option.OptionOrder.OUTPUT_ORDER;
import static io.carml.runner.option.OptionOrder.OUT_FORMAT_ORDER;
import static io.carml.runner.option.OptionOrder.PRETTY_ORDER;

import java.nio.file.Path;
import lombok.Getter;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Option;

@Getter
@Component
public class OutputOptions {

  @Option(names = {"-F", "-of", "--outformat"}, completionCandidates = OutputRdfFormats.class, defaultValue = "nq",
      order = OUT_FORMAT_ORDER,
      description = {"Output RDF format. Default: ${DEFAULT-VALUE}.", "Supported values are ${COMPLETION-CANDIDATES}"})
  private String outputRdfFormat;

  @Option(names = {"-o", "--output"}, order = OUTPUT_ORDER, description = { //
      "Output file path.", //
      "If path is directory, will default to fileName `output`.", //
      "If left empty will output to console."})
  private Path outputPath;

  @Option(names = {"-P", "--pretty"}, order = PRETTY_ORDER,
      description = "Serialize pretty printed output. (Caution: will cause in-memory output collection).")
  private boolean pretty;
}
