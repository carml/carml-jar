package io.carml.runner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.IsEqualCompressingWhiteSpace.equalToCompressingWhiteSpace;

import io.carml.runner.output.OutputHandler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(classes = {TestApplication.class})
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
    assertThat(outContent.toString(), equalToCompressingWhiteSpace("Usage: carml [-hV] [COMMAND] " //
        + "  -h, --help      Show this help message and exit. " //
        + "  -V, --version   Print version information and exit. " //
        + "Commands: " //
        + "  map "));
  }
}
