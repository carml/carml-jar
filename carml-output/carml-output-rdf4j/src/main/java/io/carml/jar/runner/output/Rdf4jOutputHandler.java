package io.carml.jar.runner.output;

import static io.carml.jar.runner.format.Rdf4JFormats.determineRdfFormat;
import static io.carml.jar.runner.format.RdfFormat.n3;
import static io.carml.jar.runner.format.RdfFormat.nq;
import static io.carml.jar.runner.format.RdfFormat.nt;
import static io.carml.jar.runner.format.RdfFormat.trig;
import static io.carml.jar.runner.format.RdfFormat.trigs;
import static io.carml.jar.runner.format.RdfFormat.trix;
import static io.carml.jar.runner.format.RdfFormat.ttl;
import static io.carml.jar.runner.format.RdfFormat.ttls;

import io.carml.jar.runner.CarmlJarException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.WriterConfig;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.util.annotation.NonNull;

@Component
public class Rdf4jOutputHandler implements OutputHandler {

  private static final Set<String> STREAMING_FORMAT = Set.of(nt.name(), nq.name());

  private static final Set<String> POTENTIALLY_STREAMING_FORMAT =
      Set.of(ttl.name(), ttls.name(), trig.name(), trigs.name(), n3.name(), trix.name());

  /**
   * Write a {@link Flux} of {@link Statement}s to the provided {@link OutputStream} as RDF in the
   * referenced RDF Format in a pretty fashion.<br>
   * <br>
   * This the model will be fully collected in-memory before writing to the {@link OutputStream}.
   *
   * @param statementFlux The {@link Flux} of {@link Statement}s.
   * @param format The RDF format reference.
   * @param namespaces The namespaces to apply.
   * @param outputStream The {@link OutputStream}.
   * @return the number of statements written.
   */
  @Override
  public long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull String format,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
    var config = new WriterConfig();
    config.set(BasicWriterSettings.PRETTY_PRINT, true);
    config.set(BasicWriterSettings.INLINE_BLANK_NODES, true);
    Model model = statementFlux.collect(ModelCollector.toModel())
        .block();

    assert model != null;
    namespaces.forEach(model::setNamespace);
    Rio.write(model, outputStream, determineRdfFormat(format), config);

    return model.size();
  }

  /**
   * Write a {@link Flux} of {@link Statement}s to the provided {@link OutputStream} as RDF in the
   * referenced RDF format in a streaming fashion.<br>
   * <br>
   * The output written to the {@link OutputStream} on a statement by statement basis.
   *
   * @param statementFlux The {@link Flux} of {@link Statement}s.
   * @param format The RDF format reference.
   * @param namespaces The namespaces to apply.
   * @param outputStream The {@link OutputStream}.
   * @return the number of statements written.
   */
  @Override
  public long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull String format,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
    RDFWriter rdfWriter = Rio.createWriter(determineRdfFormat(format), outputStream);
    AtomicLong counter = new AtomicLong();

    try {
      rdfWriter.startRDF();
      namespaces.forEach(rdfWriter::handleNamespace);

      statementFlux.doOnNext(rdfWriter::handleStatement)
          .doOnNext(statement -> counter.getAndIncrement())
          .blockLast();

      rdfWriter.endRDF();
    } catch (RDFHandlerException rdfHandlerException) {
      throw new CarmlJarException("Exception occurred while writing output.", rdfHandlerException);
    }

    return counter.get();
  }

  /**
   * Determines whether the RDF format reference is streamable taking into account the value of
   * {@code pretty}.
   *
   * @param format The RDF format reference.
   * @param pretty The {@code boolean} value.
   * @return {@code boolean} value indicating streamability.
   */
  @Override
  public boolean isFormatStreamable(@NonNull String format, boolean pretty) {
    return STREAMING_FORMAT.contains(format) || (!pretty && POTENTIALLY_STREAMING_FORMAT.contains(format));
  }
}
