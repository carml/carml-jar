package io.carml.runner;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static io.carml.runner.TestApplication.getStringForPath;
import static io.carml.runner.TestApplication.getTestSourcePath;
import static io.carml.runner.format.RdfFormat.nq;
import static io.carml.runner.format.RdfFormat.ttl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static picocli.CommandLine.ExitCode.USAGE;

import io.carml.runner.output.OutputHandler;
import java.io.BufferedOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import reactor.core.publisher.Flux;

@SpringBootTest(classes = {TestApplication.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CarmlMapCommandTest {

  private static final Path TEST_PATH = getTestSourcePath(Paths.get("carml-map-command"));

  @Autowired
  private CarmlRunner carmlRunner;

  @SpyBean
  private OutputHandler outputHandler;

  @Captor
  private ArgumentCaptor<Flux<Statement>> statementsCaptor;

  @TempDir
  private Path tmpOutputDir;

  @Test
  void givenMappingAndRelativeSourceLocationArgs_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation};

    // When
    carmlRunner.run(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(2));
  }

  @Test
  void givenMappingAndMappingFormatArgs_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var mappingFormat = "ttl";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-f", mappingFormat};

    // When
    carmlRunner.run(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(2));
  }

  @Test
  void givenMappingAndStreamableOutputFormatArgs_whenMapCommandRun_thenReturnStreamingFormattedOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var outputFormat = "ttl";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-of", outputFormat};

    // When
    carmlRunner.run(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(ttl), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(2));
  }

  @Test
  void givenMappingAndOutputFormatAndPrettyArgs_whenMapCommandRun_thenReturnPrettyFormattedOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var outputFormat = "ttl";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-of", outputFormat, "-P"};

    // When
    carmlRunner.run(args);

    // Then
    verify(outputHandler).outputPretty(statementsCaptor.capture(), eq(ttl), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(2));
  }

  @Test
  void givenMappingAndOutputPathArgs_whenMapCommandRun_thenOutputsNqToPathViaBufferedOutputStream() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var outputPath = tmpOutputDir.resolve("out.nq")
        .toString();
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-o", outputPath};

    // When
    carmlRunner.run(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq), eq(Map.of()),
        isA(BufferedOutputStream.class));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(2));
  }

  @Test
  void givenIncorrectPrefixMapping_whenMapCommandRun_thenExitWithUsageCode() throws Exception {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args =
        new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-pm", relativeSourceLocation, "-p", "foo"};

    // When
    var exitCode = catchSystemExit(() -> {
      carmlRunner.run(args);
      System.exit(carmlRunner.getExitCode());
    });

    // Then
    assertThat(exitCode, is(USAGE));
  }
}
