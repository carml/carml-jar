package io.carml.runner.input;

import io.carml.runner.format.RdfFormat;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.rdf4j.model.Model;
import org.springframework.stereotype.Component;

@Component
public interface ModelLoader {

  Model loadModel(List<Path> paths, RdfFormat rdfFormat);
}
