package io.carml.jar.runner;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static io.carml.jar.runner.TestApplication.getStringForPath;
import static io.carml.jar.runner.TestApplication.getTestSourcePath;
import static io.carml.jar.runner.format.RdfFormat.nq;
import static io.carml.jar.runner.format.RdfFormat.ttl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static picocli.CommandLine.ExitCode.USAGE;

import io.carml.jar.runner.output.OutputHandler;
import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.model.util.Models;
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
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(2));
  }

  @Test
  void givenMappingAndSystemIn_whenMapCommandRun_thenReturnStreamingNqOutput() throws Exception {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.inputstream.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping};
    var stdin = System.in;
    var inputStream = IOUtils.toInputStream(String.format("id,make%n1,Toyota%n2,Mercedes"), StandardCharsets.UTF_8);
    System.setIn(inputStream);

    // When
    carmlRunner.run(args);

    // Then
    System.setIn(stdin);
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
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
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
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
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(ttl.name()), eq(Map.of()), eq(System.out));
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
    var prefixes = "schema=https://schema.org/";
    var args =
        new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-p", prefixes, "-of", outputFormat, "-P"};

    // When
    carmlRunner.run(args);

    // Then
    verify(outputHandler).outputPretty(statementsCaptor.capture(), eq(ttl.name()),
        eq(Map.of("schema", "https://schema.org/")), eq(System.out));
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
    var prefixes = "schema=https://schema.org/";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-p", prefixes, "-o", outputPath};

    // When
    carmlRunner.run(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()),
        eq(Map.of("schema", "https://schema.org/")), isA(BufferedOutputStream.class));
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

  @Test
  void givenLimitArg_whenMapCommandRun_thenReturnLimitedOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var limit = "1";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-l", limit};

    // When
    carmlRunner.run(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(1));
  }

  @Test
  void givenBaseIriArg_whenMapCommandRun_thenReturnOutputWithBaseIri() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var baseIri = "https://foo.bar/";
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-b", baseIri};

    // When
    carmlRunner.run(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(2));
    Models.subject(model)
        .ifPresentOrElse(subject -> assertThat(subject.stringValue(), startsWith(baseIri)),
            () -> fail("Expected subject but non found."));
  }
}
