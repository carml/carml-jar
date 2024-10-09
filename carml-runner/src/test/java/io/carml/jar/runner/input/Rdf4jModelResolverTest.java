package io.carml.jar.runner.input;

import static io.carml.jar.runner.TestApplication.getTestSourcePath;
import static io.carml.jar.runner.format.RdfFormat.nq;
import static io.carml.jar.runner.format.RdfFormat.ttl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.carml.jar.runner.CarmlJarException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class Rdf4jModelResolverTest {

  private static final Path TEST_PATH = getTestSourcePath(Paths.get("input", "rdf4j-model-resolver"));

  private final Rdf4jModelLoader rdf4jModelLoader = new Rdf4jModelLoader();

  @Test
  void givenSingleFile_whenLoadModelWithFormat_thenReturnModelForFile() {
    // Given
    var paths = List.of(TEST_PATH.resolve(Paths.get("rml", "ttl", "test-1.rml.ttl")));

    // When
    var model = rdf4jModelLoader.loadModel(paths, ttl);

    // Then
    assertThat(model.size(), is(8));
  }

  @Test
  void givenSingleFileWithSupportedFileExtension_whenLoadModelWithoutFormat_thenReturnModelForFile() {
    // Given
    var paths = List.of(TEST_PATH.resolve(Paths.get("rml", "ttl", "test-1.rml.ttl")));

    // When
    var model = rdf4jModelLoader.loadModel(paths, null);

    // Then
    assertThat(model.size(), is(8));
  }

  @Test
  void givenSingleFileWithUnsupportedFileExtension_whenLoadModelWithoutFormat_thenThrowException() {
    // Given
    var path = TEST_PATH.resolve(Paths.get("rml-2", "foo", "test-1.rml.foo"));
    var paths = List.of(path);

    // When
    var carmlJarException = assertThrows(CarmlJarException.class, () -> rdf4jModelLoader.loadModel(paths, null));

    // Then
    var expectedMsg = String.format("Could not determine mapping format by file extension for path '%s'", path);
    assertThat(carmlJarException.getMessage(), is(expectedMsg));
  }

  @Test
  void givenDirectoryWithUniformlyFormattedFiles_whenLoadModelWithFormat_thenReturnModelForFiles() {
    // Given
    var paths = List.of(TEST_PATH.resolve(Paths.get("rml", "ttl")));

    // When
    var model = rdf4jModelLoader.loadModel(paths, ttl);

    // Then
    assertThat(model.size(), is(16));
  }

  @Test
  void givenDirectoryWithFilesOfMixedSupportedFormats_whenLoadModelWithoutFormat_thenReturnModelForFiles() {
    // Given
    var paths = List.of(TEST_PATH.resolve(Paths.get("rml")));

    // When
    var model = rdf4jModelLoader.loadModel(paths, null);

    // Then
    assertThat(model.size(), is(24));
  }

  @Test
  void givenDirectoryWithFilesOfSupportedFormats_whenLoadModelWithOtherFormat_thenThrowException() {
    // Given
    var paths = List.of(TEST_PATH.resolve(Paths.get("rml", "ttl")));

    // When
    var carmlJarException = assertThrows(CarmlJarException.class, () -> rdf4jModelLoader.loadModel(paths, nq));

    // Then
    assertThat(carmlJarException.getMessage(), startsWith("Exception occurred while parsing"));
  }

  @Test
  void givenDirectoryWithFilesOfSupportedAndUnsupportedFormats_whenLoadModelWithNoFormat_thenThrowException() {
    // Given
    var paths = List.of(TEST_PATH.resolve(Paths.get("rml-2")));

    // When
    var carmlJarException = assertThrows(CarmlJarException.class, () -> rdf4jModelLoader.loadModel(paths, nq));

    // Then
    assertThat(carmlJarException.getMessage(), startsWith("Exception occurred while parsing"));
  }
}
