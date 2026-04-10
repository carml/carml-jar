package io.carml.jar.runner.plan;

import io.carml.jar.runner.plan.MappingAnalyzer.MappingAnalysis;
import io.carml.jar.runner.plan.MappingAnalyzer.SourceAnalysis;
import io.carml.jar.runner.plan.MappingAnalyzer.ViewAnalysis;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Formats a {@link MappingAnalysis} as human-readable text and prints it to an output stream.
 * Includes source summary, per-view analysis, and a recommended {@code carml map} command.
 */
public final class PlanReporter {

  private final PrintStream out;

  public PlanReporter(PrintStream out) {
    this.out = out;
  }

  /**
   * Prints the full plan report.
   *
   * @param analysis the mapping analysis result
   * @param mappingFiles the mapping file paths (for the recommended command)
   * @param relativeSourceLocation the relative source location (for the recommended command), or null
   */
  public void report(MappingAnalysis analysis, List<String> mappingFiles, String relativeSourceLocation) {
    out.println();
    out.println("=== CARML Mapping Plan ===");

    reportSources(analysis);
    reportViews(analysis);
    reportRecommendation(analysis, mappingFiles, relativeSourceLocation);
  }

  private void reportSources(MappingAnalysis analysis) {
    out.println();
    out.println("--- Sources ---");
    for (var source : analysis.sources()) {
      out.printf("  [%s] %s (%s, %s)%n", source.id(), source.name(), source.type(), source.formulation());
      if (source.location() != null && !source.location()
          .equals(source.name())) {
        out.printf("      Location: %s%n", source.location());
      }
      if (source.estimatedRows() != null) {
        out.printf("      Estimated iterations: %,d%n", source.estimatedRows());
      }
    }
  }

  private void reportViews(MappingAnalysis analysis) {
    var totalGroups = analysis.views()
        .stream()
        .mapToInt(v -> v.decomposition()
            .groupCount())
        .sum();
    var totalMaps = analysis.views()
        .size();

    out.println();
    if (totalGroups > totalMaps) {
      out.printf("--- TriplesMaps (%d, resolved to %d evaluation groups) ---%n", totalMaps, totalGroups);
    } else {
      out.printf("--- TriplesMaps (%d) ---%n", totalMaps);
    }

    for (var view : analysis.views()) {
      reportView(view);
    }
  }

  private void reportView(ViewAnalysis view) {
    out.println();
    out.printf("  %s%n", view.triplesMapName());
    out.printf("    Source: [%s]%n", view.sourceId());
    out.printf("    Fields: %d | POMs: %d | Joins: %s%n", view.fieldCount(), view.pomCount(),
        formatJoins(view.joins()));
    formatAnnotations(view.annotations());

    if (view.decomposition()
        .decomposed()) {
      out.printf("    Decomposition: %d groups%n", view.decomposition()
          .groupCount());
    }

    out.printf("    Dedup: %s%n", view.dedupStrategy()
        .toUpperCase());
    out.printf("    In-process DB compatible: %s%n", view.inProcessDbCompatible() ? "Yes" : "No");
    out.printf("    → %s%n", view.recommendedEvaluator());
  }

  private static String formatJoins(MappingAnalyzer.JoinInfo joins) {
    if (joins.leftJoinCount() == 0 && joins.innerJoinCount() == 0) {
      return "none";
    }
    var parts = new ArrayList<String>();
    if (joins.leftJoinCount() > 0) {
      parts.add("%d left".formatted(joins.leftJoinCount()));
    }
    if (joins.innerJoinCount() > 0) {
      parts.add("%d inner".formatted(joins.innerJoinCount()));
    }
    var suffix = joins.hasCrossSourceJoins() ? " (cross-source)" : "";
    return String.join(", ", parts) + suffix;
  }

  private void formatAnnotations(MappingAnalyzer.AnnotationInfo ann) {
    var parts = new ArrayList<String>();
    if (ann.pkCount() > 0) {
      parts.add("PK(%s)".formatted(String.join(", ", ann.pkFields())));
    }
    if (ann.fkCount() > 0) {
      parts.add("FK×%d".formatted(ann.fkCount()));
    }
    if (ann.uniqueCount() > 0) {
      parts.add("Unique×%d".formatted(ann.uniqueCount()));
    }
    if (ann.notNullCount() > 0) {
      parts.add("NotNull×%d".formatted(ann.notNullCount()));
    }
    if (!parts.isEmpty()) {
      out.printf("    Annotations: %s%n", String.join(", ", parts));
    }
  }

  private void reportRecommendation(MappingAnalysis analysis, List<String> mappingFiles,
      String relativeSourceLocation) {
    out.println();
    out.println("--- Recommendation ---");

    var recommendedReactive = analysis.views()
        .stream()
        .filter(v -> "reactive".equals(v.recommendedEvaluator()))
        .count();
    var recommendedDuckDb = analysis.views()
        .stream()
        .filter(v -> "in-process-db".equals(v.recommendedEvaluator()))
        .count();

    if (recommendedDuckDb > 0 && recommendedReactive > 0) {
      out.printf("  Mixed evaluators recommended: %d reactive, %d in-process-db.%n", recommendedReactive,
          recommendedDuckDb);
      out.println("  Use auto mode (default) to select the best evaluator per TriplesMap.");
    } else if (recommendedDuckDb > 0) {
      out.println("  All TriplesMaps recommended for in-process DB evaluator.");
    } else {
      out.println("  All TriplesMaps recommended for reactive evaluator.");
    }

    var maxRows = analysis.sources()
        .stream()
        .map(SourceAnalysis::estimatedRows)
        .filter(Objects::nonNull)
        .mapToLong(Long::longValue)
        .max()
        .orElse(0);

    var hasJoins = analysis.views()
        .stream()
        .anyMatch(viewAnalysis -> viewAnalysis.joins()
            .leftJoinCount() > 0
            || viewAnalysis.joins()
                .innerJoinCount() > 0);
    var anyRowCountUnknown = analysis.sources()
        .stream()
        .anyMatch(sourceAnalysis -> sourceAnalysis.estimatedRows() == null);
    // Recommend spill for large data, or for joins when data size is unknown or large
    var needsSpill = maxRows > 100_000 || (hasJoins && (anyRowCountUnknown || maxRows > 10_000));

    if (maxRows > 0) {
      out.printf("  Largest source: ~%,d iterations.%n", maxRows);
    }
    if (needsSpill) {
      out.println("  Spill to disk recommended for large data / join safety.");
    }

    // Build recommended command
    out.println();
    var cmd = new StringBuilder("  carml map");
    for (var file : mappingFiles) {
      cmd.append(" -m ")
          .append(file);
    }
    if (relativeSourceLocation != null) {
      cmd.append(" -rsl ")
          .append(relativeSourceLocation);
    }
    // Recommend explicit evaluator when all views agree; omit for mixed (auto is default)
    if (recommendedDuckDb > 0 && recommendedReactive == 0) {
      cmd.append(" -E in-process-db");
    } else if (recommendedReactive > 0 && recommendedDuckDb == 0) {
      cmd.append(" -E reactive");
    }
    if (needsSpill) {
      cmd.append(" --spill-to-disk");
    }
    cmd.append(" -F nt");

    out.println(cmd);
    out.println();
  }
}
