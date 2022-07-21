package io.carml.runner.output;

import static io.carml.runner.format.Rdf4JFormats.determineRdfFormat;

import io.carml.runner.CarmlJarException;
import io.carml.runner.format.RdfFormat;
import java.io.OutputStream;
import java.util.Map;
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

  @Override
  public long outputPretty(@NonNull Flux<Statement> statementFlux, @NonNull RdfFormat format,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
    var config = new WriterConfig();
    config.set(BasicWriterSettings.PRETTY_PRINT, true);
    config.set(BasicWriterSettings.INLINE_BLANK_NODES, true);
    Model model = statementFlux.collect(ModelCollector.toModel())
        .block();

    assert model != null;
    namespaces.forEach(model::setNamespace);
    Rio.write(model, outputStream, determineRdfFormat(format.name()), config);

    return model.size();
  }

  @Override
  public long outputStreaming(@NonNull Flux<Statement> statementFlux, @NonNull RdfFormat format,
      @NonNull Map<String, String> namespaces, @NonNull OutputStream outputStream) {
    RDFWriter rdfWriter = Rio.createWriter(determineRdfFormat(format.name()), outputStream);
    AtomicLong counter = new AtomicLong();

    try {
      rdfWriter.startRDF();
      namespaces.forEach(rdfWriter::handleNamespace);

      statementFlux.doOnNext(statement -> handleStatementWrite(statement, rdfWriter, counter))
          .blockLast();

      rdfWriter.endRDF();
    } catch (RDFHandlerException rdfHandlerException) {
      throw new CarmlJarException("Exception occurred while writing output.", rdfHandlerException);
    }

    return counter.get();
  }

  private void handleStatementWrite(Statement statement, RDFWriter rdfWriter, AtomicLong counter) {
    rdfWriter.handleStatement(statement);
    counter.getAndIncrement();
  }
}
