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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static picocli.CommandLine.ExitCode.USAGE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.carml.jar.runner.input.Rdf4jModelLoader;
import io.carml.jar.runner.option.LoggingOptions;
import io.carml.jar.runner.option.OutputRdfFormats;
import io.carml.jar.runner.output.OutputHandler;
import io.carml.jar.runner.prefix.DefaultNamespacePrefixMapper;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class CarmlMapCommandTest {

  private static final Path TEST_PATH = getTestSourcePath(Paths.get("carml-map-command"));

  private OutputHandler outputHandler;

  private CommandLine commandLine;

  @Captor
  private ArgumentCaptor<Flux<Statement>> statementsCaptor;

  @TempDir
  private Path tmpOutputDir;

  @BeforeEach
  void setUp() {
    outputHandler = spy(new TestOutputHandler());
    var modelLoader = new Rdf4jModelLoader();
    var prefixMapper = new DefaultNamespacePrefixMapper(new ObjectMapper(), new YAMLMapper());
    var mapCommand = new CarmlMapCommand(modelLoader, outputHandler, prefixMapper, List.of());

    var rdfFormats = RDFWriterRegistry.getInstance()
        .getKeys()
        .stream()
        .map(RDFFormat::getDefaultFileExtension)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    commandLine = new CommandLine(new CarmlCommand(), createFactory(new OutputRdfFormats(rdfFormats)))
        .setExecutionStrategy(LoggingOptions::executionStrategy)
        .addSubcommand("map", mapCommand);
  }

  private static CommandLine.IFactory createFactory(OutputRdfFormats outputRdfFormats) {
    return new CommandLine.IFactory() {
      @Override
      public <K> K create(Class<K> cls) throws Exception {
        if (cls.isAssignableFrom(OutputRdfFormats.class)) {
          return cls.cast(outputRdfFormats);
        }
        return CommandLine.defaultFactory()
            .create(cls);
      }
    };
  }

  @Test
  void givenMappingAndRelativeSourceLocationArgs_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(2));
  }

  @Test
  void givenMappingAndSystemIn_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.inputstream.rml.ttl");
    var args = new String[] {"map", "-m", mapping};
    var stdin = System.in;
    var inputStream = IOUtils.toInputStream(String.format("id,make%n1,Toyota%n2,Mercedes"), StandardCharsets.UTF_8);
    System.setIn(inputStream);

    // When
    commandLine.execute(args);

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
    commandLine.execute(args);

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
    commandLine.execute(args);

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
    commandLine.execute(args);

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
    commandLine.execute(args);

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
      int result = commandLine.execute(args);
      System.exit(result);
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
    commandLine.execute(args);

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
    commandLine.execute(args);

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

  @Test
  void givenReactiveEvaluatorArg_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "reactive"};

    // When
    commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
    var model = statementsCaptor.getValue()
        .collect(new ModelCollector())
        .block();
    assertThat(model.size(), is(2));
  }

  @Test
  void givenInProcessDbEvaluatorArg_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "in-process-db"};

    // When
    int exitCode = commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
    assertThat(exitCode, is(0));
  }

  @Test
  void givenSpillToDiskArg_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "--spill-to-disk"};

    // When
    int exitCode = commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
    assertThat(exitCode, is(0));
  }

  @Test
  void givenInProcessDbEvaluatorAndSpillToDiskArgs_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args =
        new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "in-process-db", "--spill-to-disk"};

    // When
    int exitCode = commandLine.execute(args);

    // Then
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
    assertThat(exitCode, is(0));
  }

  @Test
  void givenStreamEvaluatorAndSpillToDiskArg_whenMapCommandRun_thenSpillToDiskArgIgnored() {
    // Given
    var mapping = getStringForPath(TEST_PATH, "mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath(TEST_PATH, "source");
    var args = new String[] {"map", "-m", mapping, "-rsl", relativeSourceLocation, "-E", "reactive", "--spill-to-disk"};

    // When
    int exitCode = commandLine.execute(args);

    // Then - flag is silently ignored; reactive evaluator runs successfully
    verify(outputHandler).outputStreaming(statementsCaptor.capture(), eq(nq.name()), eq(Map.of()), eq(System.out));
    assertThat(exitCode, is(0));
  }

  /**
   * Test-only OutputHandler that returns stub values.
   */
  private static class TestOutputHandler implements OutputHandler {

    @Override
    public long outputPretty(Flux<Statement> statementFlux, String format, Map<String, String> namespaces,
        OutputStream outputStream) {
      return 1;
    }

    @Override
    public long outputStreaming(Flux<Statement> statementFlux, String format, Map<String, String> namespaces,
        OutputStream outputStream) {
      return 2;
    }

    @Override
    public boolean isFormatStreamable(String rdfFormat, boolean pretty) {
      return !pretty;
    }
  }
}
