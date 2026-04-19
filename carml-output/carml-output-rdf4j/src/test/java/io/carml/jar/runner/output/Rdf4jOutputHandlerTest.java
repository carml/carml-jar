package io.carml.jar.runner.output;

import static io.carml.jar.runner.format.RdfFormat.brf;
import static io.carml.jar.runner.format.RdfFormat.ndjsonld;
import static io.carml.jar.runner.format.RdfFormat.nq;
import static io.carml.jar.runner.format.RdfFormat.nt;
import static io.carml.jar.runner.format.RdfFormat.rj;
import static io.carml.jar.runner.format.RdfFormat.trigs;
import static io.carml.jar.runner.format.RdfFormat.trix;
import static io.carml.jar.runner.format.RdfFormat.ttl;
import static io.carml.jar.runner.format.RdfFormat.ttls;
import static org.eclipse.rdf4j.model.util.Statements.statement;
import static org.eclipse.rdf4j.model.util.Values.bnode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.carml.output.FastSerializerProvider;
import io.carml.output.RdfSerializer;
import io.carml.output.RdfSerializerFactory;
import io.carml.output.RdfSerializerProvider;
import io.carml.output.RioSerializerProvider;
import io.carml.output.SerializerMode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
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

    // RioModelSerializer configures PRETTY_PRINT=true + INLINE_BLANK_NODES=true to preserve the
    // pre-SPI Rdf4jOutputHandler output shape (inline blank nodes render as "[]" instead of "_:id")
    assertThat(outContent.toString(), containsString("[] a [] ."));
  }

  @Test
  void givenStatementsAndNtFormat_whenOutputStreaming_thenOutputNt() {
    var statementFlux = generateStatementsFor("foo", 5);

    var nrOfStatements = rdf4jOutputHandler.outputStreaming(statementFlux, nt.name(), Map.of(), System.out);

    assertThat(nrOfStatements, is(5L));
    assertThat(StringUtils.countMatches(outContent.toString(), RDF.TYPE.stringValue()), is(5));
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
    // Rdf4jOutputHandler uses RioSerializerProvider (+ FastSerializerProvider for nt/nq).
    // Rio supports STREAMING for all CLI aliases, so pretty=false yields true for every alias
    // below; pretty=true always yields false for non-byte-streaming formats.
    return Stream.of(//
        Arguments.of("ttl", true, false), //
        Arguments.of("ttl", false, true), //
        Arguments.of("nt", false, true), //
        Arguments.of("nt", true, true), //
        Arguments.of("nq", false, true), //
        Arguments.of("nq", true, true), //
        Arguments.of("trig", false, true), //
        Arguments.of("trig", true, false), //
        Arguments.of("ttls", false, true), //
        Arguments.of("ttls", true, false), //
        Arguments.of("trigs", false, true), //
        Arguments.of("trigs", true, false), //
        Arguments.of("ndjsonld", false, true), //
        Arguments.of("ndjsonld", true, false), //
        Arguments.of("brf", false, true), //
        Arguments.of("brf", true, false), //
        Arguments.of("trix", false, true), //
        Arguments.of("trix", true, false), //
        Arguments.of("rj", false, true), //
        Arguments.of("rj", true, false), //
        Arguments.of("unknown", false, false), //
        Arguments.of("unknown", true, false));
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

  @Test
  void injectedFactory_isUsedWhenCreatingSerializer() {
    // Given: a spy-wrapped factory so we can verify delegation to the injected instance.
    var factory = spy(RdfSerializerFactory.of(List.of(new FastSerializerProvider(), new RioSerializerProvider())));
    var handler = new Rdf4jOutputHandler(factory);
    var statementFlux = generateStatementsFor("foo", 2);

    // When
    var nrOfStatements = handler.outputStreaming(statementFlux, nt.name(), Map.of(), System.out);

    // Then the handler went through the injected factory (not a default-constructed one).
    assertThat(nrOfStatements, is(2L));
    verify(factory).createSerializer(eq(nt.name()), eq(SerializerMode.STREAMING));
  }

  @Test
  void injectedFactory_serializerLifecycleIsInvoked() {
    // Given a stubbed factory that returns a mocked serializer; assert start/write/end/close all fire.
    var serializer = mock(RdfSerializer.class);
    var provider = mock(RdfSerializerProvider.class);
    var namespaces = Map.of("ex", "https://example.com/");
    when(provider.supports(eq("ttl"), eq(SerializerMode.STREAMING))).thenReturn(true);
    when(provider.createSerializer(eq("ttl"), eq(SerializerMode.STREAMING))).thenReturn(serializer);
    when(provider.priority()).thenReturn(42);
    var factory = RdfSerializerFactory.of(List.of(provider));
    var handler = new Rdf4jOutputHandler(factory);
    var statementFlux = generateStatementsFor("baz", 3);

    var nrOfStatements = handler.outputStreaming(statementFlux, "ttl", namespaces, System.out);

    assertThat(nrOfStatements, is(3L));
    var inOrder = inOrder(serializer);
    inOrder.verify(serializer)
        .start(eq(System.out), eq(namespaces));
    inOrder.verify(serializer, times(3))
        .write(any());
    inOrder.verify(serializer)
        .end();
    inOrder.verify(serializer)
        .close();
  }

  @Test
  void roundTrip_outputStreamingTtl_isIsomorphic() throws Exception {
    // Given
    var vf = SimpleValueFactory.getInstance();
    var s = vf.createIRI("http://example.org/s");
    var stmt = vf.createStatement(s, RDFS.LABEL, vf.createLiteral("hello"));
    var output = new ByteArrayOutputStream();

    // When
    rdf4jOutputHandler.outputStreaming(Flux.just(stmt), "ttl", Map.of("rdfs", RDFS.NAMESPACE), new PrintStream(output));

    // Then
    try (var in = new ByteArrayInputStream(output.toByteArray())) {
      var parsed = Rio.parse(in, "", RDFFormat.TURTLE);
      var expected = new org.eclipse.rdf4j.model.impl.LinkedHashModel();
      expected.add(stmt);
      assertThat(Models.isomorphic(parsed, expected), is(true));
    }
  }

  @Test
  void roundTrip_outputPrettyTtl_isIsomorphic() throws Exception {
    // Given
    var vf = SimpleValueFactory.getInstance();
    var s = vf.createIRI("http://example.org/s");
    var stmt = vf.createStatement(s, RDFS.LABEL, vf.createLiteral("hello"));
    var output = new ByteArrayOutputStream();

    // When
    rdf4jOutputHandler.outputPretty(Flux.just(stmt), "ttl", Map.of("rdfs", RDFS.NAMESPACE), new PrintStream(output));

    // Then
    try (var in = new ByteArrayInputStream(output.toByteArray())) {
      var parsed = Rio.parse(in, "", RDFFormat.TURTLE);
      var expected = new org.eclipse.rdf4j.model.impl.LinkedHashModel();
      expected.add(stmt);
      assertThat(Models.isomorphic(parsed, expected), is(true));
    }
  }

  // ---- Extended CLI-alias round-trips (Task 7.10 regression guard: ttls, trigs, brf, trix) ----

  static Stream<Arguments> triplesAliasAndFormatArgs() {
    return Stream.of(Arguments.of(ttls.name(), RDFFormat.TURTLESTAR), Arguments.of(trix.name(), RDFFormat.TRIX),
        Arguments.of(brf.name(), RDFFormat.BINARY), Arguments.of(ndjsonld.name(), RDFFormat.NDJSONLD),
        Arguments.of(rj.name(), RDFFormat.RDFJSON));
  }

  @ParameterizedTest(name = "CLI alias {0} round-trips via outputPretty")
  @MethodSource("triplesAliasAndFormatArgs")
  void outputPretty_tripleCliAlias_roundTripsIsomorphically(String cliAlias, RDFFormat rdf4jFormat) throws Exception {
    // Given
    var vf = SimpleValueFactory.getInstance();
    var s = vf.createIRI("http://example.org/s");
    var stmt = vf.createStatement(s, RDFS.LABEL, vf.createLiteral("hello"));
    var output = new ByteArrayOutputStream();

    // When
    rdf4jOutputHandler.outputPretty(Flux.just(stmt), cliAlias, Map.of("rdfs", RDFS.NAMESPACE), new PrintStream(output));

    // Then
    try (var in = new ByteArrayInputStream(output.toByteArray())) {
      var parsed = Rio.parse(in, "", rdf4jFormat);
      var expected = new org.eclipse.rdf4j.model.impl.LinkedHashModel();
      expected.add(stmt);
      assertThat(Models.isomorphic(parsed, expected), is(true));
    }
  }

  @Test
  void outputPretty_trigStarCliAlias_roundTripsIsomorphically() throws Exception {
    // Given — TriG-star needs a quad (graph) to distinguish from Turtle-star.
    var vf = SimpleValueFactory.getInstance();
    var s = vf.createIRI("http://example.org/s");
    var g = vf.createIRI("http://example.org/g");
    var stmt = vf.createStatement(s, RDFS.LABEL, vf.createLiteral("hello"), g);
    var output = new ByteArrayOutputStream();

    // When
    rdf4jOutputHandler.outputPretty(Flux.just(stmt), trigs.name(), Map.of("ex", "http://example.org/"),
        new PrintStream(output));

    // Then
    try (var in = new ByteArrayInputStream(output.toByteArray())) {
      var parsed = Rio.parse(in, "", RDFFormat.TRIGSTAR);
      var expected = new org.eclipse.rdf4j.model.impl.LinkedHashModel();
      expected.add(stmt);
      assertThat(Models.isomorphic(parsed, expected), is(true));
    }
  }

  // ---- Streaming-mode CLI-alias round-trips (Task 7.10 regression guard: ttls, brf, trix) ----

  static Stream<Arguments> streamingTriplesAliasAndFormatArgs() {
    return Stream.of(Arguments.of(ttls.name(), RDFFormat.TURTLESTAR), Arguments.of(trix.name(), RDFFormat.TRIX),
        Arguments.of(brf.name(), RDFFormat.BINARY));
  }

  @ParameterizedTest(name = "CLI alias {0} round-trips via outputStreaming")
  @MethodSource("streamingTriplesAliasAndFormatArgs")
  void outputStreaming_tripleCliAlias_roundTripsIsomorphically(String cliAlias, RDFFormat rdf4jFormat)
      throws Exception {
    // Given
    var vf = SimpleValueFactory.getInstance();
    var s = vf.createIRI("http://example.org/s");
    var stmt = vf.createStatement(s, RDFS.LABEL, vf.createLiteral("hello"));
    var output = new ByteArrayOutputStream();

    // When
    rdf4jOutputHandler.outputStreaming(Flux.just(stmt), cliAlias, Map.of("rdfs", RDFS.NAMESPACE),
        new PrintStream(output));

    // Then
    try (var in = new ByteArrayInputStream(output.toByteArray())) {
      var parsed = Rio.parse(in, "", rdf4jFormat);
      var expected = new org.eclipse.rdf4j.model.impl.LinkedHashModel();
      expected.add(stmt);
      assertThat(Models.isomorphic(parsed, expected), is(true));
    }
  }

  @Test
  void outputStreaming_trigStarCliAlias_roundTripsIsomorphically() throws Exception {
    // Given — TriG-star needs a quad (graph) to exercise the quad streaming path.
    var vf = SimpleValueFactory.getInstance();
    var s = vf.createIRI("http://example.org/s");
    var g = vf.createIRI("http://example.org/g");
    var stmt = vf.createStatement(s, RDFS.LABEL, vf.createLiteral("hello"), g);
    var output = new ByteArrayOutputStream();

    // When
    rdf4jOutputHandler.outputStreaming(Flux.just(stmt), trigs.name(), Map.of("ex", "http://example.org/"),
        new PrintStream(output));

    // Then
    try (var in = new ByteArrayInputStream(output.toByteArray())) {
      var parsed = Rio.parse(in, "", RDFFormat.TRIGSTAR);
      var expected = new org.eclipse.rdf4j.model.impl.LinkedHashModel();
      expected.add(stmt);
      assertThat(Models.isomorphic(parsed, expected), is(true));
    }
  }

  // ---- Empty-Flux handling (Task 7.10): both modes must return 0 and not throw ----

  @Test
  void outputPretty_withEmptyFlux_returnsZeroAndEmitsValidEmptyOutput() {
    var nrOfStatements =
        rdf4jOutputHandler.outputPretty(Flux.empty(), ttl.name(), Map.of("ex", "http://example.org/"), System.out);

    assertThat(nrOfStatements, is(0L));
  }

  @Test
  void outputStreaming_withEmptyFlux_returnsZeroAndEmitsValidEmptyOutput() {
    var nrOfStatements = rdf4jOutputHandler.outputStreaming(Flux.empty(), nt.name(), Map.of(), System.out);

    assertThat(nrOfStatements, is(0L));
  }

  @Test
  void constructorWithoutFactory_usesDefaultFactory() {
    var handler = new Rdf4jOutputHandler();
    // Default constructor yields a non-null handler usable against standard formats.
    assertSame(handler.getClass(), Rdf4jOutputHandler.class);
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
