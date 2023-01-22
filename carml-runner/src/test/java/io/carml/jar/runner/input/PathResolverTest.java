package io.carml.jar.runner.input;

import static io.carml.jar.runner.TestApplication.getTestSourcePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.carml.jar.runner.CarmlJarException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class PathResolverTest {

  private static final Path TEST_PATH = getTestSourcePath(Paths.get("input", "path-resolver"));

  @Test
  void givenPathToSingleFile_whenResolvePaths_thenReturnPathToSingleFile() {
    // Given
    var inputPath = TEST_PATH.resolve(Paths.get("dir-1", "file-1-1"));
    var inputPaths = List.of(inputPath);

    // When
    var paths = FilePathResolver.resolveFilePaths(inputPaths);

    // Then
    assertThat(paths, is(List.of(inputPath)));
  }

  @Test
  void givenPathToDir_whenResolvePaths_thenReturnAllFilePathsInDir() {
    // Given
    var inputPaths = List.of(TEST_PATH);

    // When
    var paths = FilePathResolver.resolveFilePaths(inputPaths);

    // Then
    var file111 = TEST_PATH.resolve(Paths.get("dir-1", "dir-1-1", "file-1-1-1"));
    var file11 = TEST_PATH.resolve(Paths.get("dir-1", "file-1-1"));
    var file21 = TEST_PATH.resolve(Paths.get("dir-2", "file-2-1"));

    assertThat(paths, containsInAnyOrder(file111, file11, file21));
  }

  @Test
  void givenNonExistentPath_whenResolvePaths_thenThrowException() {
    // Given
    var nonExistentPath = TEST_PATH.resolve("foo");
    var inputPaths = List.of(nonExistentPath);

    // When
    var carmlJarException = assertThrows(CarmlJarException.class, () -> FilePathResolver.resolveFilePaths(inputPaths));

    // Then
    assertThat(carmlJarException.getMessage(),
        is(String.format("Exception occurred while reading path %s", nonExistentPath)));
  }
}
