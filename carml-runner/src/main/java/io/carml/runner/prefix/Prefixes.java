package io.carml.runner.prefix;

public final class Prefixes {

  private Prefixes() {}

  public static final String INLINE_PREFIX_DECLARATION_SEPARATOR = "=";

  public static boolean isInlinePrefixMapping(String prefixDeclaration) {
    return prefixDeclaration.contains(INLINE_PREFIX_DECLARATION_SEPARATOR);
  }
}
