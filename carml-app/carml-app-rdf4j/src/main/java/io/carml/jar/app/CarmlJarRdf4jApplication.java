package io.carml.jar.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {R2dbcAutoConfiguration.class})
@ComponentScan("io.carml.jar")
public class CarmlJarRdf4jApplication {

  public static void main(String... args) {
    System.exit(SpringApplication.exit(SpringApplication.run(CarmlJarRdf4jApplication.class, args)));
  }
}
