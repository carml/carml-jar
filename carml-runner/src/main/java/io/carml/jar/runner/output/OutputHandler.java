package io.carml.jar.runner.output;

import java.io.OutputStream;
import java.util.Map;
import lombok.NonNull;
import org.eclipse.rdf4j.model.Statement;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public interface OutputHandler {

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
}
