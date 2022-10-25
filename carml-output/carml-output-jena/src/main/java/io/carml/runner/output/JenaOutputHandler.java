package io.carml.runner.output;

import static io.carml.runner.format.JenaLangs.determineLang;
import static io.carml.runner.format.JenaLangs.supportsGraphs;
import static io.carml.util.jena.JenaCollectors.toDatasetGraph;

import io.carml.runner.format.JenaLangs;
import io.carml.util.jena.JenaConverters;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.StreamRDFWriter;
import org.eclipse.rdf4j.model.Statement;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class JenaOutputHandler implements OutputHandler {

  private static final Set<String> STREAMING_FORMAT = Set.of("nt", "nq");

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

    if (supportsGraphs(lang)) {
      RDFDataMgr.write(outputStream, datasetGraph, lang);
    } else {
      RDFDataMgr.write(outputStream, datasetGraph.getDefaultGraph(), lang);
      datasetGraph.listGraphNodes()
          .forEachRemaining(node -> RDFDataMgr.write(outputStream, datasetGraph.getGraph(node), lang));
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
    var counter = new AtomicLong();
    var lang = determineLang(rdfFormat);
    var streamRdf = StreamRDFWriter.getWriterStream(outputStream, lang);
    streamRdf.start();
    namespaces.forEach(streamRdf::prefix);

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
