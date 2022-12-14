package io.carml.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("io.carml")
public class CarmlJarRdf4jApplication {

  public static void main(String... args) {
    System.exit(SpringApplication.exit(SpringApplication.run(CarmlJarRdf4jApplication.class, args)));
  }
}
