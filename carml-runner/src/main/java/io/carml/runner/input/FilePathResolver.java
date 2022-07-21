package io.carml.runner.input;

import io.carml.runner.CarmlJarException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FilePathResolver {

  private FilePathResolver() {}

  /**
   * Find all file {@link Path}s in the file tree starting from given {@link Path}.
   *
   * @param paths the {@link List} of {@link Path}s to search through.
   * @return the {@link List} of file {@link Path}s.
   */
  public static List<Path> resolveFilePaths(List<Path> paths) {
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
