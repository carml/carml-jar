package io.carml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CarmlJarApplication {
  public static void main(String... args) {
    System.exit(SpringApplication.exit(SpringApplication.run(CarmlJarApplication.class, args)));
  }
}
