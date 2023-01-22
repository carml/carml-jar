package io.carml.jar.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("io.carml.jar")
public class CarmlJarJenaApplication {

  public static void main(String... args) {
    System.exit(SpringApplication.exit(SpringApplication.run(CarmlJarJenaApplication.class, args)));
  }
}
