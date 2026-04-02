package io.carml.jar.runner.output;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import org.eclipse.rdf4j.model.Statement;
import reactor.core.publisher.Flux;

public interface OutputHandler {

  /**
   * RDF format names that support byte-level streaming (one encoded triple/quad per byte array).
   * These formats have line-based syntax (N-Triples, N-Quads) and can be serialized without buffering
   * the full model.
   */
  Set<String> BYTE_STREAMING_FORMATS = Set.of("nt", "nq");

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
  long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull String rdfFormat,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream);


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
  long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull String rdfFormat,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream);

  /**
   * Determines whether the RDF format reference is streamable taking into account the value of
   * {@code pretty}.
   *
   * @param rdfFormat The RDF format reference.
   * @param pretty The {@code boolean} value.
   * @return {@code boolean} value indicating streamability.
   */
  boolean isFormatStreamable(@NonNull String rdfFormat, boolean pretty);

  /**
   * Write a {@link Flux} of pre-encoded byte arrays to the provided {@link OutputStream}. Each byte
   * array is a single encoded triple/quad line (e.g. N-Triples or N-Quads). This bypasses
   * {@link Statement} object creation entirely for maximum throughput.
   *
   * @param byteFlux The {@link Flux} of encoded byte arrays.
   * @param outputStream The {@link OutputStream}.
   * @return the number of byte arrays (triples) written.
   */
  default long outputStreamingBytes(@NonNull Flux<byte[]> byteFlux, @NonNull OutputStream outputStream) {
    var count = byteFlux.doOnNext(encodedTriple -> {
      try {
        outputStream.write(encodedTriple);
      } catch (IOException e) {
        throw new UncheckedIOException("Error writing byte output", e);
      }
    })
        .count()
        .block();
    try {
      outputStream.flush();
    } catch (IOException e) {
      throw new UncheckedIOException("Error flushing byte output", e);
    }
    return count != null ? count : 0;
  }
}
