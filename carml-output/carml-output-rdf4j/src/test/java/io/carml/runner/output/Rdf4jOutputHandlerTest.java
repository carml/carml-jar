package io.carml.runner.output;

import static io.carml.runner.format.RdfFormat.NQ;
import static io.carml.runner.format.RdfFormat.TTL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class Rdf4jOutputHandlerTest {

  private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

  private final Rdf4jOutputHandler rdf4jOutputHandler = new Rdf4jOutputHandler();

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
  void givenStatementsAndNqFormat_whenOutputPretty_thenOutputNq() {
    // Given
    var statementFlux = generateStatementsFor("foo", 5);

    // When
    var nrOfStatements = rdf4jOutputHandler.outputPretty(statementFlux, NQ, Map.of(), System.out);

    // Then
    assertThat(nrOfStatements, is(5L));
    assertThat(StringUtils.countMatches(outContent.toString(), RDF.TYPE.stringValue()), is(5));
  }

  @Test
  void givenStatementsAndTtlFormatAndNamespaces_whenOutputPretty_thenOutputPrefixedTtl() {
    // Given
    var statementFlux = generateStatementsFor("bar", 4);
    var namespaces = Map.of("ex", "https://example.com/");

    // When
    var nrOfStatements = rdf4jOutputHandler.outputPretty(statementFlux, TTL, namespaces, System.out);

    // Then
    assertThat(nrOfStatements, is(4L));

    var namespaceDeclaration = "@prefix ex: <https://example.com/> .";
    assertThat(StringUtils.countMatches(outContent.toString(), namespaceDeclaration), is(1));

    var prettyTtlStatementWithBlankNodesInlined = "[] a [] .";
    assertThat(StringUtils.countMatches(outContent.toString(), prettyTtlStatementWithBlankNodesInlined), is(4));
  }

  @Test
  void givenStatementsAndNqFormat_whenOutputStreaming_thenOutputNq() {
    // Given
    var statementFlux = generateStatementsFor("foo", 5);

    // When
    var nrOfStatements = rdf4jOutputHandler.outputStreaming(statementFlux, NQ, Map.of(), System.out);

    // Then
    assertThat(nrOfStatements, is(5L));
    assertThat(StringUtils.countMatches(outContent.toString(), RDF.TYPE.stringValue()), is(5));
  }

  @Test
  void givenStatementsAndTtlFormatAndNamespaces_whenOutputStreaming_thenOutputPrefixedTtl() {
    // Given
    var statementFlux = generateStatementsFor("bar", 4);
    var namespaces = Map.of("ex", "https://example.com/");

    // When
    var nrOfStatements = rdf4jOutputHandler.outputStreaming(statementFlux, TTL, namespaces, System.out);

    // Then
    assertThat(nrOfStatements, is(4L));

    assertThat(StringUtils.countMatches(outContent.toString(), "@prefix ex: <https://example.com/> ."), is(1));

    assertThat(StringUtils.countMatches(outContent.toString(), "_:sub-bar-0 a _:obj-bar-0 ."), is(1));
    assertThat(StringUtils.countMatches(outContent.toString(), "_:sub-bar-1 a _:obj-bar-1 ."), is(1));
    assertThat(StringUtils.countMatches(outContent.toString(), "_:sub-bar-2 a _:obj-bar-2 ."), is(1));
    assertThat(StringUtils.countMatches(outContent.toString(), "_:sub-bar-3 a _:obj-bar-3 ."), is(1));
  }

  private static Flux<Statement> generateStatementsFor(String id, int amount) {
    List<Statement> statements = new ArrayList<>();
    for (int i = 0; i < amount; i++) {
      statements.add(generateStatementFor(id, i));
    }

    return Flux.fromIterable(statements);
  }

  private static Statement generateStatementFor(String id, int number) {
    return VALUE_FACTORY.createStatement(VALUE_FACTORY.createBNode(String.format("sub-%s-%s", id, number)), RDF.TYPE,
        VALUE_FACTORY.createBNode(String.format("obj-%s-%s", id, number)));
  }
}
