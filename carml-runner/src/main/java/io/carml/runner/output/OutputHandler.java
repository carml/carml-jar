package io.carml.runner.output;

import io.carml.runner.format.RdfFormat;
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
   * provided {@link RdfFormat} in a pretty fashion.
   *
   * @param statementFlux the {@link Flux} of {@link Statement}s.
   * @param format the {@link RdfFormat}.
   * @param namespaces the namespaces toe apply.
   * @param outputStream the {@link OutputStream}.
   * @return the amount of statements written.
   */
  long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull RdfFormat format,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream);


  /**
   * Write a {@link Flux} of {@link Statement}s to the provided {@link OutputStream} as RDF in the
   * provided {@link RdfFormat} in a streaming fashion.
   *
   * @param statementFlux the {@link Flux} of {@link Statement}s.
   * @param format the {@link RdfFormat}.
   * @param namespaces the namespaces toe apply.
   * @param outputStream the {@link OutputStream}.
   * @return the amount of statements written.
   */
  long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull RdfFormat format,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream);
}
