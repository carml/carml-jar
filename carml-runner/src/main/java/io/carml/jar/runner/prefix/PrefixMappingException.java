package io.carml.jar.runner.prefix;

public class PrefixMappingException extends RuntimeException {

  private static final long serialVersionUID = -6685749532177893801L;

  public PrefixMappingException(String message) {
    super(message);
  }

  public PrefixMappingException(String message, Throwable cause) {
    super(message, cause);
  }
}
