package io.carml.jar.runner;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.text.IsEqualCompressingWhiteSpace.equalToCompressingWhiteSpace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.carml.jar.runner.input.Rdf4jModelLoader;
import io.carml.jar.runner.option.LoggingOptions;
import io.carml.jar.runner.option.OutputRdfFormats;
import io.carml.jar.runner.output.OutputHandler;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.hamcrest.MatcherAssert;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;
import reactor.core.publisher.Flux;

class CarmlRunnerTest {

  private CommandLine commandLine;

  private ByteArrayOutputStream outContent;

  private final PrintStream originalOut = System.out;

  @BeforeEach
  void setUp() {
    // Reset log level to ERROR before each test to avoid cross-test contamination
    resetLogLevel();

    outContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent));

    var modelLoader = new Rdf4jModelLoader();
    var prefixMapper =
        new io.carml.jar.runner.prefix.DefaultNamespacePrefixMapper(new ObjectMapper(), new YAMLMapper());
    var outputHandler = new StubOutputHandler();
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

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    resetLogLevel();
  }

  private static void resetLogLevel() {
    LoggerContext loggerContext = LoggerContext.getContext(false);
    LoggerConfig carmlLoggerConfig = loggerContext.getConfiguration()
        .getLoggerConfig(LoggingOptions.CARML_LOGGER);
    carmlLoggerConfig.setLevel(Level.ERROR);
    loggerContext.updateLoggers();
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
  void givenArgHelp_whenRun_thenReturnExpectedHelpMessage() {
    // Given
    var args = new String[] {"-h"};

    // When
    commandLine.execute(args);

    // Then
    assertThat(outContent.toString(), equalToCompressingWhiteSpace("Usage:  [-hVv] [COMMAND] " //
        + "  -h, --help      Show this help message and exit. " //
        + "  -V, --version   Print version information and exit. " //
        + "  -v, --verbose   Specify multiple -v or --verbose options to increase verbosity. " //
        + "                  For example `-v -v`, or `-vv` or `--verbose --verbose` " //
        + "Commands: " //
        + "  map "));
  }

  static Stream<Arguments> exitCodeTestArguments() {
    return Stream.of(//
        Arguments.of(List.of("-h"), 0), //
        Arguments.of(List.of("map", "-m", "some/non/existent/path"), 1), //
        Arguments.of(List.of("foo"), 2));
  }

  @ParameterizedTest
  @MethodSource("exitCodeTestArguments")
  void givenArgs_whenRunAndSystemExit_thenExitCodeIsCorrect(List<String> argList, int expectedExitCode)
      throws Exception {
    // Given
    var args = argList.toArray(String[]::new);

    // When
    var exitCode = catchSystemExit(() -> {
      int result = commandLine.execute(args);
      System.exit(result);
    });

    // Then
    assertThat(exitCode, is(expectedExitCode));
  }

  static Stream<Arguments> loggingTestArguments() {
    return Stream.of(//
        Arguments.of(List.of(), Level.ERROR), //
        Arguments.of(List.of("-v"), Level.INFO), //
        Arguments.of(List.of("-vv"), Level.DEBUG), //
        Arguments.of(List.of("-vvv"), Level.TRACE), //
        Arguments.of(List.of("-vvvvvv"), Level.TRACE), //
        Arguments.of(List.of("--verbose"), Level.INFO), //
        Arguments.of(List.of("-v", "-v", "-v"), Level.TRACE), //
        Arguments.of(List.of("--verbose", "--verbose"), Level.DEBUG));
  }

  @ParameterizedTest
  @MethodSource("loggingTestArguments")
  void givenVerbosityOption_whenMapCommandRun_thenCorrectLogLevelIsSet(List<String> verbosity, Level logLevel) {
    // Given
    var args = verbosity.toArray(String[]::new);

    // When
    commandLine.execute(args);

    // Then
    MatcherAssert.assertThat(TestApplication.getLogLevel(LoggingOptions.CARML_LOGGER), is(logLevel));
  }

  /**
   * Stub OutputHandler for tests that do not need real RDF output.
   */
  private static class StubOutputHandler implements OutputHandler {

    @Override
    public long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull String format,
        @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
      return 1;
    }

    @Override
    public long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull String format,
        @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
      return 2;
    }

    @Override
    public boolean isFormatStreamable(@NonNull String rdfFormat, boolean pretty) {
      return !pretty;
    }

    @Override
    public long outputStreamingBytes(@NonNull Flux<byte[]> byteFlux, @NonNull OutputStream outputStream) {
      return 2;
    }
  }
}
