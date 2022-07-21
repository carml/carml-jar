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

  long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull RdfFormat format,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream);

  long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull RdfFormat format,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream);
}
