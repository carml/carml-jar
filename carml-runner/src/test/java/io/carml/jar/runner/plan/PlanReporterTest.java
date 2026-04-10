package io.carml.jar.runner.plan;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.carml.jar.runner.plan.MappingAnalyzer.AnnotationInfo;
import io.carml.jar.runner.plan.MappingAnalyzer.DecompositionInfo;
import io.carml.jar.runner.plan.MappingAnalyzer.JoinInfo;
import io.carml.jar.runner.plan.MappingAnalyzer.MappingAnalysis;
import io.carml.jar.runner.plan.MappingAnalyzer.SourceAnalysis;
import io.carml.jar.runner.plan.MappingAnalyzer.ViewAnalysis;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlanReporterTest {

  private static final JoinInfo NO_JOINS = new JoinInfo(0, 0, false);

  private static final AnnotationInfo NO_ANNOTATIONS = new AnnotationInfo(0, 0, 0, 0, List.of());

  private static final DecompositionInfo NO_DECOMPOSITION = new DecompositionInfo(false, 1);

  @Nested
  class RecommendedCommand {

    @Test
    void allReactive_recommendsExplicitReactiveFlag() {
      var analysis = analysisWithViews(view("TM1", "1", "reactive"), view("TM2", "2", "reactive"));

      var output = report(analysis);

      assertThat(output, containsString("-E reactive"));
      assertThat(output, not(containsString("-E in-process-db")));
      assertThat(output, containsString("All TriplesMaps recommended for reactive evaluator."));
    }

    @Test
    void allInProcessDb_recommendsExplicitInProcessDbFlag() {
      var analysis = analysisWithViews(view("TM1", "1", "in-process-db"), view("TM2", "2", "in-process-db"));

      var output = report(analysis);

      assertThat(output, containsString("-E in-process-db"));
      assertThat(output, not(containsString("-E reactive")));
      assertThat(output, containsString("All TriplesMaps recommended for in-process DB evaluator."));
    }

    @Test
    void mixed_omitsEvaluatorFlag() {
      var analysis = analysisWithViews(view("TM1", "1", "reactive"), view("TM2", "2", "in-process-db"));

      var output = report(analysis);

      assertThat(output, not(containsString("-E reactive")));
      assertThat(output, not(containsString("-E in-process-db")));
      assertThat(output, containsString("Mixed evaluators recommended: 1 reactive, 1 in-process-db."));
      assertThat(output, containsString("Use auto mode (default) to select the best evaluator per TriplesMap."));
    }

    @Test
    void mixed_multipleOfEach_showsCounts() {
      var analysis = analysisWithViews(view("TM1", "1", "reactive"), view("TM2", "2", "reactive"),
          view("TM3", "3", "reactive"), view("TM4", "4", "in-process-db"));

      var output = report(analysis);

      assertThat(output, containsString("Mixed evaluators recommended: 3 reactive, 1 in-process-db."));
    }

    @Test
    void singleReactiveView_recommendsReactive() {
      var analysis = analysisWithViews(view("TM1", "1", "reactive"));

      var output = report(analysis);

      assertThat(output, containsString("-E reactive"));
    }

    @Test
    void singleInProcessDbView_recommendsInProcessDb() {
      var analysis = analysisWithViews(view("TM1", "1", "in-process-db"));

      var output = report(analysis);

      assertThat(output, containsString("-E in-process-db"));
    }
  }

  @Nested
  class SpillToDisk {

    @Test
    void largeSource_recommendsSpill() {
      var sources = List.of(source("1", "data.csv", 200_000L));
      var views = List.of(view("TM1", "1", "in-process-db"));
      var analysis = new MappingAnalysis(sources, views);

      var output = report(analysis);

      assertThat(output, containsString("--spill-to-disk"));
    }

    @Test
    void smallSource_noSpill() {
      var sources = List.of(source("1", "data.csv", 100L));
      var views = List.of(view("TM1", "1", "reactive"));
      var analysis = new MappingAnalysis(sources, views);

      var output = report(analysis);

      assertThat(output, not(containsString("--spill-to-disk")));
    }

    @Test
    void joinsWithUnknownRows_recommendsSpill() {
      var sources = List.of(source("1", "data.csv", null));
      var views = List.of(viewWithJoins("TM1", "1", "in-process-db", 1, 0));
      var analysis = new MappingAnalysis(sources, views);

      var output = report(analysis);

      assertThat(output, containsString("--spill-to-disk"));
    }

    @Test
    void joinsWithSmallKnownRows_noSpill() {
      var sources = List.of(source("1", "data.csv", 100L));
      var views = List.of(viewWithJoins("TM1", "1", "reactive", 1, 0));
      var analysis = new MappingAnalysis(sources, views);

      var output = report(analysis);

      assertThat(output, not(containsString("--spill-to-disk")));
    }

    @Test
    void joinsWithMediumRows_recommendsSpill() {
      var sources = List.of(source("1", "data.csv", 50_000L));
      var views = List.of(viewWithJoins("TM1", "1", "in-process-db", 1, 0));
      var analysis = new MappingAnalysis(sources, views);

      var output = report(analysis);

      assertThat(output, containsString("--spill-to-disk"));
    }
  }

  @Nested
  class MappingFiles {

    @Test
    void includesMappingFile() {
      var analysis = analysisWithViews(view("TM1", "1", "reactive"));

      var output = reportWithFiles(analysis, List.of("mapping.ttl"), null);

      assertThat(output, containsString("-m mapping.ttl"));
    }

    @Test
    void includesMultipleMappingFiles() {
      var analysis = analysisWithViews(view("TM1", "1", "reactive"));

      var output = reportWithFiles(analysis, List.of("a.ttl", "b.ttl"), null);

      assertThat(output, containsString("-m a.ttl -m b.ttl"));
    }

    @Test
    void includesRelativeSourceLocation() {
      var analysis = analysisWithViews(view("TM1", "1", "reactive"));

      var output = reportWithFiles(analysis, List.of("mapping.ttl"), "/data");

      assertThat(output, containsString("-rsl /data"));
    }

    @Test
    void omitsRelativeSourceLocationWhenNull() {
      var analysis = analysisWithViews(view("TM1", "1", "reactive"));

      var output = reportWithFiles(analysis, List.of("mapping.ttl"), null);

      assertThat(output, not(containsString("-rsl")));
    }
  }

  // --- Helpers ---

  private static MappingAnalysis analysisWithViews(ViewAnalysis... views) {
    var sources = List.of(source("1", "data.csv", 100L));
    return new MappingAnalysis(sources, List.of(views));
  }

  private static SourceAnalysis source(String id, String name, Long rows) {
    return new SourceAnalysis(id, name, "CSV", "CSV", name, rows);
  }

  private static ViewAnalysis view(String name, String sourceId, String evaluator) {
    return new ViewAnalysis(name, sourceId, 5, 3, NO_JOINS, NO_ANNOTATIONS, NO_DECOMPOSITION, "none", true, evaluator,
        List.of());
  }

  private static ViewAnalysis viewWithJoins(String name, String sourceId, String evaluator, int leftJoins,
      int innerJoins) {
    var joins = new JoinInfo(leftJoins, innerJoins, false);
    return new ViewAnalysis(name, sourceId, 5, 3, joins, NO_ANNOTATIONS, NO_DECOMPOSITION, "none", true, evaluator,
        List.of());
  }

  private static String report(MappingAnalysis analysis) {
    return reportWithFiles(analysis, List.of("mapping.ttl"), null);
  }

  private static String reportWithFiles(MappingAnalysis analysis, List<String> mappingFiles,
      String relativeSourceLocation) {
    var baos = new ByteArrayOutputStream();
    var out = new PrintStream(baos, true, StandardCharsets.UTF_8);
    new PlanReporter(out).report(analysis, mappingFiles, relativeSourceLocation);
    return baos.toString(StandardCharsets.UTF_8);
  }
}
