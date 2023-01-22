package io.carml.jar.runner.format;

import io.carml.jar.runner.CarmlJarException;
import java.util.Set;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;

public final class JenaLangs {

  private JenaLangs() {}

  private static final Set<Lang> GRAPH_LANGS =
      Set.of(Lang.JSONLD, Lang.JSONLD10, Lang.JSONLD11, Lang.TRIG, Lang.N3, Lang.NQUADS, Lang.TRIX);

  public static Lang determineLang(String rdfFormat) {
    var lang = RDFLanguages.fileExtToLang(rdfFormat);
    if (lang != null) {
      return lang;
    }

    throw new CarmlJarException(String.format("Unsupported RDF Format reference specified: '%s'.", rdfFormat));
  }

  public static boolean supportsGraphs(Lang lang) {
    return GRAPH_LANGS.contains(lang);
  }
}
