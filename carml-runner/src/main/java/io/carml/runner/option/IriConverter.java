package io.carml.runner.option;

import static org.eclipse.rdf4j.model.util.Values.iri;

import org.eclipse.rdf4j.model.IRI;
import picocli.CommandLine;
import picocli.CommandLine.TypeConversionException;

public class IriConverter implements CommandLine.ITypeConverter<IRI> {

  @Override
  public IRI convert(String iriString) throws Exception {
    try {
      return iri(iriString);
    } catch (IllegalArgumentException illegalArgumentException) {
      throw new TypeConversionException(String.format("`%s` is not a valid IRI", iriString));
    }
  }
}
