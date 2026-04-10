package io.carml.jar.runner;

import static picocli.CommandLine.ExitCode.OK;

import io.carml.engine.MappingResolver;
import io.carml.engine.ResolvedMapping;
import io.carml.jar.runner.input.ModelLoader;
import io.carml.jar.runner.option.MappingFileOptions;
import io.carml.jar.runner.option.OptionOrder;
import io.carml.jar.runner.plan.MappingAnalyzer;
import io.carml.jar.runner.plan.PlanInterviewer;
import io.carml.jar.runner.plan.PlanReporter;
import io.carml.util.Mappings;
import io.carml.util.RmlMappingLoader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * CLI command that analyzes an RML mapping and recommends the optimal execution strategy. Inspects
 * source types, join patterns, structural annotations, and view decomposition potential. Optionally
 * interviews the user for source characteristics that cannot be determined from the mapping alone.
 *
 * <p>
 * Usage: {@code carml plan -m mapping.ttl [-rsl ./data] [--source-rows name=count] [-i]}
 */
@Command(name = "plan", sortOptions = false, sortSynopsis = false, mixinStandardHelpOptions = true,
    description = "Analyze a mapping and recommend the optimal execution strategy.")
public class CarmlPlanCommand implements Callable<Integer> {

  private static final Logger LOG = LogManager.getLogger(CarmlPlanCommand.class);

  @Mixin
  private MappingFileOptions mappingFileOptions;

  @Option(names = {"--source-iterations"}, order = OptionOrder.SOURCE_ROWS_ORDER,
      description = "Estimated iteration count per source (name=count). Repeatable."
          + " An iteration corresponds to one logical iteration: a row in CSV/SQL, a matched object in JSON,"
          + " or a matched element in XML.")
  private Map<String, Long> sourceRows;

  @Option(names = {"-i", "--interactive"}, order = OptionOrder.INTERACTIVE_ORDER, defaultValue = "false",
      description = "Prompt for missing source characteristics.")
  private boolean interactive;

  private final ModelLoader modelLoader;

  public CarmlPlanCommand(ModelLoader modelLoader) {
    this.modelLoader = modelLoader;
  }

  @Override
  public Integer call() {
    var paths = mappingFileOptions.getGroup()
        .getMappingFiles();
    var mappingFormat = mappingFileOptions.getGroup()
        .getMappingFileRdfFormat();

    LOG.info("Loading mapping from {} ...", () -> paths);
    var mappingModel = modelLoader.loadModel(paths, mappingFormat);
    var triplesMaps = RmlMappingLoader.build()
        .load(mappingModel);

    if (triplesMaps.isEmpty()) {
      LOG.warn("No TriplesMaps found in mapping.");
      System.out.println("\nNo TriplesMaps found in the mapping file(s).");
      return OK;
    }

    // Filter out functionValue inner TriplesMaps — their logicalSource is just a context
    // marker for expression evaluation, not a real data source or mappable unit.
    var mappableTriplesMaps = Mappings.filterMappable(triplesMaps);

    LOG.info("Resolving {} TriplesMaps ...", mappableTriplesMaps.size());
    List<ResolvedMapping> resolvedMappings;
    try {
      resolvedMappings = MappingResolver.resolve(mappableTriplesMaps);
    } catch (Exception e) {
      LOG.error("Failed to resolve mapping: {}", e.getMessage());
      return picocli.CommandLine.ExitCode.SOFTWARE;
    }

    LOG.info("Analyzing mapping structure ...");
    var analysis = MappingAnalyzer.analyze(mappableTriplesMaps, resolvedMappings);

    // Apply --source-iterations CLI options. Match against both the display name (which may
    // include the iterator, e.g. "data.json [$.people[*]]") and the base location/filename
    // (e.g. "data.json"), so users can use just the filename for convenience.
    if (sourceRows != null && !sourceRows.isEmpty()) {
      var updatedSources = analysis.sources()
          .stream()
          .map(source -> {
            var preset = sourceRows.get(source.name());
            if (preset == null && source.location() != null) {
              // Try matching by location (full path) and by filename only
              preset = sourceRows.get(source.location());
              if (preset == null) {
                var lastSlash = source.location()
                    .lastIndexOf('/');
                var filename = lastSlash >= 0 ? source.location()
                    .substring(lastSlash + 1) : source.location();
                preset = sourceRows.get(filename);
              }
            }
            return preset != null ? source.withEstimatedRows(preset) : source;
          })
          .toList();
      analysis = new MappingAnalyzer.MappingAnalysis(updatedSources, analysis.views());
    }

    // Interactive interview for missing source metadata
    if (interactive && analysis.sources()
        .stream()
        .anyMatch(s -> s.estimatedRows() == null)) {
      var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      var interviewer = new PlanInterviewer(reader, System.err);
      System.err.println("\nSource information needed:");
      analysis = interviewer.interview(analysis);
    }

    // Refresh evaluator recommendations now that source row estimates are available
    analysis = analysis.refreshRecommendations();

    // Report
    var mappingFiles = paths.stream()
        .map(Path::toString)
        .toList();
    var relSrcLoc = mappingFileOptions.getGroup()
        .getRelativeSourceLocation()
        .map(Path::toString)
        .orElse(null);
    var reporter = new PlanReporter(System.out);
    reporter.report(analysis, mappingFiles, relSrcLoc);

    return OK;
  }
}
