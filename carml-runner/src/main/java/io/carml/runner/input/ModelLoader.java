package io.carml.runner.input;

import io.carml.runner.format.RdfFormat;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.rdf4j.model.Model;
import org.springframework.stereotype.Component;

@Component
public interface ModelLoader {

  /**
   * Load {@link Model} from list paths, using all file {@link Path}s in the file tree starting from
   * given {@link Path}.
   *
   * @param paths the {@link List} of {@link Path}s to search through and load from.
   * @param rdfFormat the RDF format of the files.
   * @return the {@link Model}.
   */
  Model loadModel(List<Path> paths, RdfFormat rdfFormat);
}
