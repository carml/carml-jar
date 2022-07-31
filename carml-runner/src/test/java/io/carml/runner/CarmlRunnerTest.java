package io.carml.runner;

import static io.carml.runner.TestApplication.getLogLevel;
import static io.carml.runner.option.LoggingOptions.CARML_LOGGER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.text.IsEqualCompressingWhiteSpace.equalToCompressingWhiteSpace;

import io.carml.runner.output.OutputHandler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(classes = {TestApplication.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CarmlRunnerTest {

  @Autowired
  private CarmlRunner carmlRunner;

  @MockBean
  private OutputHandler outputHandler;

  private ByteArrayOutputStream outContent;

  private final PrintStream originalOut = System.out;

  @BeforeEach
  public void beforeEach() {
    outContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent));
  }

  @AfterEach
  public void afterEach() {
    System.setOut(originalOut);
  }

  @Test
  void givenArgHelp_whenRun_thenReturnExpectedHelpMessage() {
    // Given
    var args = new String[] {"-h"};

    // When
    carmlRunner.run(args);

    // Then
    assertThat(outContent.toString(), equalToCompressingWhiteSpace("Usage: carml [-hVv] [COMMAND] " //
        + "  -h, --help      Show this help message and exit. " //
        + "  -V, --version   Print version information and exit. " //
        + "  -v, --verbose   Specify multiple -v or --verbose options to increase verbosity. " //
        + "                  For example `-v -v`, or `-vv` or `--verbose --verbose` " //
        + "Commands: " //
        + "  map "));
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
    carmlRunner.run(args);

    // Then
    assertThat(getLogLevel(CARML_LOGGER), is(logLevel));
  }
}
