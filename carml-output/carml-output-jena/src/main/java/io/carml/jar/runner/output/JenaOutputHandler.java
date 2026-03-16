package io.carml.jar.runner.output;

import static io.carml.jar.runner.format.JenaLangs.determineLang;
import static io.carml.jar.runner.format.JenaLangs.supportsGraphs;
import static io.carml.jar.runner.format.RdfFormat.nq;
import static io.carml.jar.runner.format.RdfFormat.nt;
import static io.carml.util.jena.JenaCollectors.toDatasetGraph;

import io.carml.jar.runner.format.JenaLangs;
import io.carml.output.FastNQuadsSerializer;
import io.carml.output.FastNTriplesSerializer;
import io.carml.util.jena.JenaConverters;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RIOT;
import org.apache.jena.riot.system.StreamRDFWriter;
import org.apache.jena.sparql.util.Context;
import org.eclipse.rdf4j.model.Statement;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class JenaOutputHandler implements OutputHandler {

  private static final Set<String> STREAMING_FORMAT = Set.of(nt.name(), nq.name());

  /**
   * Write a {@link Flux} of {@link Statement}s to the provided {@link OutputStream} as RDF in the
   * referenced RDF format in a pretty fashion.
   *
   * @param statementFlux The {@link Flux} of {@link Statement}s.
   * @param rdfFormat The RDF format reference.
   * @param namespaces The namespaces toe apply.
   * @param outputStream The {@link OutputStream}.
   * @return the number of statements written.
   */
  @Override
  public long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull String rdfFormat,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
    var counter = new AtomicLong();
    var datasetGraph = statementFlux.map(JenaConverters::toQuad)
        .doOnNext(quad -> counter.getAndIncrement())
        .collect(toDatasetGraph())
        .block();

    assert datasetGraph != null;

    var lang = determineLang(rdfFormat);
    var prefixes = datasetGraph.prefixes();
    namespaces.forEach(prefixes::add);

    // TODO remove directive style override when switching to RDF 1.2
    if (supportsGraphs(lang)) {
      RDFWriter.source(datasetGraph)
          .lang(lang)
          .set(RIOT.symTurtleDirectiveStyle, "at")
          .output(outputStream);
    } else {
      RDFWriter.source(datasetGraph.getDefaultGraph())
          .lang(lang)
          .set(RIOT.symTurtleDirectiveStyle, "at")
          .output(outputStream);
      datasetGraph.listGraphNodes()
          .forEachRemaining(node -> RDFWriter.source(datasetGraph.getGraph(node))
              .lang(lang)
              .set(RIOT.symTurtleDirectiveStyle, "at")
              .output(outputStream));
    }

    return counter.get();
  }

  /**
   * Write a {@link Flux} of {@link Statement}s to the provided {@link OutputStream} as RDF in the
   * referenced RDF format in a streaming fashion.
   *
   * @param statementFlux The {@link Flux} of {@link Statement}s.
   * @param rdfFormat The RDF format reference.
   * @param namespaces The namespaces toe apply.
   * @param outputStream The {@link OutputStream}.
   * @return the number of statements written.
   */
  @Override
  public long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull String rdfFormat,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
    if (STREAMING_FORMAT.contains(rdfFormat)) {
      return outputStreamingFast(statementFlux, rdfFormat, outputStream);
    }

    return outputStreamingJena(statementFlux, rdfFormat, namespaces, outputStream);
  }

  private long outputStreamingFast(Flux<Statement> statementFlux, String rdfFormat, OutputStream outputStream) {
    if (nt.name()
        .equals(rdfFormat)) {
      return FastNTriplesSerializer.withDefaults()
          .serialize(statementFlux, outputStream);
    }
    return FastNQuadsSerializer.withDefaults()
        .serialize(statementFlux, outputStream);
  }

  private long outputStreamingJena(Flux<Statement> statementFlux, String rdfFormat, Map<String, String> namespaces,
      OutputStream outputStream) {
    var lang = determineLang(rdfFormat);
    // TODO remove directive style override when switching to RDF 1.2
    var context = new Context();
    context.set(RIOT.symTurtleDirectiveStyle, "at");
    var streamRdf = StreamRDFWriter.getWriterStream(outputStream, lang, context);
    streamRdf.start();
    namespaces.forEach(streamRdf::prefix);
    var counter = new AtomicLong();

    statementFlux.map(JenaConverters::toQuad)
        .doOnNext(streamRdf::quad)
        .doOnNext(quad -> counter.getAndIncrement())
        .blockLast();

    streamRdf.finish();

    return counter.get();
  }

  /**
   * Determines whether the RDF format reference is streamable taking into account the value of
   * {@code pretty}.
   *
   * @param rdfFormat The RDF format reference.
   * @param pretty The {@code boolean} value.
   * @return {@code boolean} value indicating streamability.
   */
  @Override
  public boolean isFormatStreamable(@NonNull String rdfFormat, boolean pretty) {
    return STREAMING_FORMAT.contains(rdfFormat)
        || (!pretty && StreamRDFWriter.registered(JenaLangs.determineLang(rdfFormat)));
  }
}
