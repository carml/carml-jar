package io.carml.jar.runner.output;

import io.carml.output.RdfSerializerFactory;
import io.carml.output.SerializerMode;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.eclipse.rdf4j.model.Statement;
import reactor.core.publisher.Flux;

/**
 * {@link OutputHandler} that delegates RDF serialization to the {@link RdfSerializerFactory} SPI.
 * Provider selection (FastSerializer for N-Triples/N-Quads byte streaming, Jena when on the
 * classpath for broad format coverage, Rio as the baseline fallback) is driven by provider priority
 * and format/mode support, not by this handler.
 *
 * <p>
 * This handler is responsible only for:
 * <ul>
 * <li>Selecting {@link SerializerMode#PRETTY} vs {@link SerializerMode#STREAMING} based on which
 * public entry point the caller invoked.</li>
 * <li>Orchestrating the {@link Flux}&#x2192;serializer write loop and counting emitted
 * statements.</li>
 * <li>Determining streamability via the factory's provider set.</li>
 * </ul>
 */
public class JenaOutputHandler implements OutputHandler {

  private final RdfSerializerFactory serializerFactory;

  public JenaOutputHandler() {
    this(RdfSerializerFactory.create());
  }

  JenaOutputHandler(RdfSerializerFactory serializerFactory) {
    this.serializerFactory = serializerFactory;
  }

  /**
   * Write a {@link Flux} of {@link Statement}s to the provided {@link OutputStream} as RDF in the
   * referenced RDF format in a pretty fashion.
   *
   * @param statementFlux The {@link Flux} of {@link Statement}s.
   * @param rdfFormat The RDF format reference.
   * @param namespaces The namespaces to apply.
   * @param outputStream The {@link OutputStream}.
   * @return the number of statements written.
   */
  @Override
  public long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull String rdfFormat,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
    return write(statementFlux, rdfFormat, SerializerMode.PRETTY, namespaces, outputStream);
  }

  /**
   * Write a {@link Flux} of {@link Statement}s to the provided {@link OutputStream} as RDF in the
   * referenced RDF format in a streaming fashion.
   *
   * @param statementFlux The {@link Flux} of {@link Statement}s.
   * @param rdfFormat The RDF format reference.
   * @param namespaces The namespaces to apply.
   * @param outputStream The {@link OutputStream}.
   * @return the number of statements written.
   */
  @Override
  public long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull String rdfFormat,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
    return write(statementFlux, rdfFormat, SerializerMode.STREAMING, namespaces, outputStream);
  }

  private long write(Flux<Statement> statementFlux, String format, SerializerMode mode, Map<String, String> namespaces,
      OutputStream outputStream) {
    try (var serializer = serializerFactory.createSerializer(format, mode)) {
      serializer.start(outputStream, namespaces);
      var counter = new AtomicLong();
      statementFlux.doOnNext(serializer::write)
          .doOnNext(statement -> counter.incrementAndGet())
          .blockLast();
      serializer.end();
      return counter.get();
    }
  }

  /**
   * Determines whether the RDF format reference is streamable taking into account the value of
   * {@code pretty}.
   *
   * <p>
   * Byte-streaming formats (N-Triples, N-Quads) are always streamable regardless of the
   * {@code pretty} flag because their byte-level encoding has no pretty-print variant. For other
   * formats, streamability is derived from whether any registered provider supports
   * {@link SerializerMode#STREAMING} for the given format.
   *
   * @param rdfFormat The RDF format reference.
   * @param pretty The {@code boolean} value.
   * @return {@code boolean} value indicating streamability.
   */
  @Override
  public boolean isFormatStreamable(@NonNull String rdfFormat, boolean pretty) {
    if (BYTE_STREAMING_FORMATS.contains(rdfFormat)) {
      return true;
    }
    if (pretty) {
      return false;
    }
    return serializerFactory.getProviders()
        .stream()
        .anyMatch(provider -> provider.supports(rdfFormat, SerializerMode.STREAMING));
  }
}
