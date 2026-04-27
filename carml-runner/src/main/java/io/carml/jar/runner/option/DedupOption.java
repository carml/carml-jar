package io.carml.jar.runner.option;

import io.carml.engine.DedupMode;
import java.util.Locale;
import picocli.CommandLine.ITypeConverter;

/**
 * CLI-facing dedup option. Maps to the engine-level {@link DedupMode} via {@link #toDedupMode()}.
 * The lowercase names match the convention used by {@link EvaluatorMode}.
 */
public enum DedupOption {
  auto(DedupMode.AUTO), none(DedupMode.NONE), view(DedupMode.VIEW), full(DedupMode.FULL);

  private final DedupMode dedupMode;

  DedupOption(DedupMode dedupMode) {
    this.dedupMode = dedupMode;
  }

  public DedupMode toDedupMode() {
    return dedupMode;
  }

  public static class Converter implements ITypeConverter<DedupOption> {

    @Override
    public DedupOption convert(String value) {
      return DedupOption.valueOf(value.toLowerCase(Locale.ROOT));
    }
  }
}
