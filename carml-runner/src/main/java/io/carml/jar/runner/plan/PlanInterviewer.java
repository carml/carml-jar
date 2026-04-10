package io.carml.jar.runner.plan;

import io.carml.jar.runner.plan.MappingAnalyzer.MappingAnalysis;
import io.carml.jar.runner.plan.MappingAnalyzer.SourceAnalysis;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Interactively prompts the user for source characteristics that cannot be determined from the
 * mapping alone. Prompts are written to stderr so stdout remains clean for the plan output.
 */
public final class PlanInterviewer {

  private final BufferedReader reader;

  private final PrintStream prompt;

  public PlanInterviewer(BufferedReader reader, PrintStream prompt) {
    this.reader = reader;
    this.prompt = prompt;
  }

  /**
   * Interviews the user for missing source metadata and returns an updated analysis. Sources that
   * already have estimated iteration counts (from {@code --source-iterations} CLI option) are
   * skipped.
   *
   * @param analysis the initial analysis with potentially missing row estimates
   * @return the updated analysis with user-provided row estimates
   */
  public MappingAnalysis interview(MappingAnalysis analysis) {
    var updatedSources = analysis.sources()
        .stream()
        .map(source -> {
          if (source.estimatedRows() == null) {
            var rows = promptForRowCount(source);
            if (rows != null) {
              return source.withEstimatedRows(rows);
            }
          }
          return source;
        })
        .toList();

    return new MappingAnalysis(updatedSources, analysis.views());
  }

  private Long promptForRowCount(SourceAnalysis source) {
    prompt.printf("%n  [%s] %s (%s)%n", source.id(), source.name(), source.type());
    prompt.print("      Estimated iterations? [press Enter to skip] ");
    prompt.flush();

    try {
      var line = reader.readLine();
      if (line == null || line.isBlank()) {
        return null;
      }
      var parsed = Long.parseLong(line.trim());
      if (parsed < 0) {
        prompt.println("      (iteration count must be non-negative, skipping)");
        return null;
      }
      return parsed;
    } catch (NumberFormatException e) {
      prompt.println("      (invalid number, skipping)");
      return null;
    } catch (IOException e) {
      return null;
    }
  }
}
