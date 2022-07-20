package io.carml.runner.output;

import io.carml.runner.format.RdfFormat;
import java.io.OutputStream;
import java.util.Map;
import org.eclipse.rdf4j.model.Statement;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public interface OutputHandler {

  long outputPretty(Flux<Statement> statementFlux, RdfFormat format, Map<String, String> namespaces,
      OutputStream outputStream);

  long outputStreaming(Flux<Statement> statementFlux, RdfFormat format, Map<String, String> namespaces,
      OutputStream outputStream);
}
