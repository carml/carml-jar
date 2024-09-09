package io.carml.jar.runner;

import io.carml.engine.function.FnoFunction;
import io.carml.engine.function.FnoParam;
import java.util.Arrays;
import java.util.List;

public class GrelFunctions {

  private static final String GREL = "http://users.ugent.be/~bjdmeest/function/grel.ttl#";

  @FnoFunction(GREL + "string_split")
  public static List<String> split(@FnoParam(GREL + "valueParameter") String s,
      @FnoParam(GREL + "p_string_sep") String sep) {
    return Arrays.stream(s.split(sep))
        .toList();
  }
}
