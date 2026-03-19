package io.carml.jar.runner.option;

import picocli.CommandLine.ITypeConverter;

public enum EvaluatorMode {
  auto, reactive, in_process_db;

  @Override
  public String toString() {
    return name().replace('_', '-');
  }

  public static class Converter implements ITypeConverter<EvaluatorMode> {

    @Override
    public EvaluatorMode convert(String value) {
      return EvaluatorMode.valueOf(value.replace('-', '_'));
    }
  }
}
