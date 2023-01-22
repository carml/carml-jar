package io.carml.jar.runner;

public class CarmlJarException extends RuntimeException {

  private static final long serialVersionUID = 8234844706149053418L;

  public CarmlJarException(String message) {
    super(message);
  }

  public CarmlJarException(String message, Throwable cause) {
    super(message, cause);
  }
}
