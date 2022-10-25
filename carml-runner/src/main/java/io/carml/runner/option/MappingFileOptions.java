package io.carml.runner.option;

import static io.carml.runner.option.OptionOrder.FORMAT_ORDER;
import static io.carml.runner.option.OptionOrder.MAPPING_ORDER;
import static io.carml.runner.option.OptionOrder.REL_SRC_LOC_ORDER;

import io.carml.runner.format.RdfFormat;
import java.nio.file.Path;
import java.util.List;
import lombok.Getter;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

@Getter
public class MappingFileOptions {

  @ArgGroup(exclusive = false, order = MAPPING_ORDER, multiplicity = "1")
  private Group group;

  @Getter
  public static class Group {
    @Option(names = {"-m", "--mapping"}, order = MAPPING_ORDER, required = true,
        description = "Mapping file path(s) and/or mapping file directory path(s).")
    private List<Path> mappingFiles;

    @Option(names = {"-f", "--format"}, order = FORMAT_ORDER, description = { //
        "Mapping file RDF format:", //
        "ttl (text/turtle),", //
        "ttls (application/x-turtlestar)", //
        "nt (application/n-triples),", //
        "nq (application/n-quads),", //
        "rdf (application/rdf+xml),", //
        "jsonld (application/ld+json),", //
        "ndjsonld (application/x-ld+ndjson)", //
        "trig (application/trig),", //
        "trigs (application/x-trigstar)", //
        "n3 (text/n3),", //
        "trix (application/trix),", //
        "brf (application/x-binary-rdf),", //
        "rj (application/rdf+json)."})
    private RdfFormat mappingFileRdfFormat;

    @Option(names = {"-r", "-rsl", "--rel-src-loc"}, order = REL_SRC_LOC_ORDER,
        description = "Path from which to relatively find the sources specified in the mapping files.")
    private Path relativeSourceLocation;
  }
}
