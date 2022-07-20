package io.carml.runner;

import static io.carml.runner.TestApplication.getTestSourcePath;
import static io.carml.runner.format.RdfFormat.nq;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.carml.runner.output.OutputHandler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import reactor.core.publisher.Flux;

@SpringBootTest(classes = {TestApplication.class})
class CarmlMapCommandTest {

  private static final Path TEST_PATH = getTestSourcePath(Paths.get("carml-map-command"));

  @Autowired
  private CarmlRunner carmlRunner;

  @SpyBean
  private OutputHandler outputHandler;

  @Captor
  private ArgumentCaptor<Flux<Statement>> statementsCaptor;

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
  void givenMappingAndRelativeSourceLocationArgs_whenMapCommandRun_thenReturnStreamingNqOutput() {
    // Given
    var mapping = getStringForPath("mapping", "mapping.rml.ttl");
    var relativeSourceLocation = getStringForPath("source");
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

  private static String getStringForPath(String first, String... more) {
    return TEST_PATH.resolve(Paths.get(first, more))
        .toString();
  }

}
