package io.carml.runner.output;

import static io.carml.runner.format.RdfFormat.nq;
import static io.carml.runner.format.RdfFormat.ttl;
import static org.eclipse.rdf4j.model.util.Statements.statement;
import static org.eclipse.rdf4j.model.util.Values.bnode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;

class Rdf4jOutputHandlerTest {

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
    var nrOfStatements = rdf4jOutputHandler.outputPretty(statementFlux, nq.name(), Map.of(), System.out);

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
    var nrOfStatements = rdf4jOutputHandler.outputPretty(statementFlux, ttl.name(), namespaces, System.out);

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
    var nrOfStatements = rdf4jOutputHandler.outputStreaming(statementFlux, nq.name(), Map.of(), System.out);

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
    var nrOfStatements = rdf4jOutputHandler.outputStreaming(statementFlux, ttl.name(), namespaces, System.out);

    // Then
    assertThat(nrOfStatements, is(4L));

    assertThat(StringUtils.countMatches(outContent.toString(), "@prefix ex: <https://example.com/> ."), is(1));

    assertThat(StringUtils.countMatches(outContent.toString(), "_:sub-bar-0 a _:obj-bar-0 ."), is(1));
    assertThat(StringUtils.countMatches(outContent.toString(), "_:sub-bar-1 a _:obj-bar-1 ."), is(1));
    assertThat(StringUtils.countMatches(outContent.toString(), "_:sub-bar-2 a _:obj-bar-2 ."), is(1));
    assertThat(StringUtils.countMatches(outContent.toString(), "_:sub-bar-3 a _:obj-bar-3 ."), is(1));
  }

  static Stream<Arguments> formatStreamableArgs() {
    return Stream.of(//
        Arguments.of("ttl", true, false), //
        Arguments.of("nq", false, true), //
        Arguments.of("nq", true, true), //
        Arguments.of("trigs", false, true));
  }

  @ParameterizedTest
  @MethodSource("formatStreamableArgs")
  void givenRdfFormat_whenIsFormatStreamable_thenReturnExpected(String rdfFormat, boolean pretty,
      boolean expectedStreamable) {
    // Given
    // When
    boolean isStreamable = rdf4jOutputHandler.isFormatStreamable(rdfFormat, pretty);

    // Then
    assertThat(isStreamable, is(expectedStreamable));
  }

  private static Flux<Statement> generateStatementsFor(String id, int amount) {
    List<Statement> statements = new ArrayList<>();
    for (int i = 0; i < amount; i++) {
      statements.add(generateStatementFor(id, i));
    }

    return Flux.fromIterable(statements);
  }

  private static Statement generateStatementFor(String id, int number) {
    return statement(bnode(String.format("sub-%s-%s", id, number)), RDF.TYPE,
        bnode(String.format("obj-%s-%s", id, number)), null);
  }
}
