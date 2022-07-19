package io.carml.runner.input;

import io.carml.runner.CarmlJarException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PathResolver {

  public static List<Path> resolvePaths(List<Path> paths) {
    return paths.stream()
        .flatMap(path -> {
          try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile)
                .collect(Collectors.toList())
                .stream();
          } catch (IOException exception) {
            throw new CarmlJarException(String.format("Exception occurred while reading path %s", path), exception);
          }
        })
        .collect(Collectors.toList());
  }
}
