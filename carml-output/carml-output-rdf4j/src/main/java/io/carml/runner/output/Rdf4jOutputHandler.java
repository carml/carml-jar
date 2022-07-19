package io.carml.runner.output;

import io.carml.runner.CarmlJarException;
import io.carml.runner.CarmlMapCommand;
import io.carml.runner.model.RdfFormat;
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

@Component
public class Rdf4jOutputHandler implements OutputHandler {

  @Override
  public long outputPretty(Flux<Statement> statementFlux, RdfFormat format, Map<String, String> namespaces,
      OutputStream outputStream) {
    var config = new WriterConfig();
    config.set(BasicWriterSettings.PRETTY_PRINT, true);
    config.set(BasicWriterSettings.INLINE_BLANK_NODES, true);
    Model model = statementFlux.collect(ModelCollector.toModel())
        .block();

    assert model != null;
    namespaces.forEach(model::setNamespace);
    Rio.write(model, outputStream, CarmlMapCommand.determineRdfFormat(format.name()), config);

    return model.size();
  }

  @Override
  public long outputStreaming(Flux<Statement> statementFlux, RdfFormat format, Map<String, String> namespaces,
      OutputStream outputStream) {
    RDFWriter rdfWriter = Rio.createWriter(CarmlMapCommand.determineRdfFormat(format.name()), outputStream);
    AtomicLong counter = new AtomicLong();

    try {
      rdfWriter.startRDF();
      namespaces.forEach(rdfWriter::handleNamespace);
      statementFlux.doOnNext(statement -> {
        rdfWriter.handleStatement(statement);
        counter.getAndIncrement();
      })
          .blockLast();
      rdfWriter.endRDF();
    } catch (RDFHandlerException rdfHandlerException) {
      throw new CarmlJarException("Exception occurred while writing output.", rdfHandlerException);
    }

    return counter.get();
  }
}
