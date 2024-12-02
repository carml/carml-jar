package io.carml.jar.runner.prefix;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.NonNull;

public interface NamespacePrefixMapper {

  /**
   * Maps {@code prefixDeclaration}s to a {@link Map} of {@link String} prefix keys and {@link String}
   * name (IRI) values to be used for namespace prefix mappings in RDF serializations.
   *
   * <p>
   * A {@code prefixDeclaration} {@link String} will be treated as an inline prefix declaration when
   * it contains an '='. For, example: {@code "ex=http://example.com/"}.
   *
   * <p>
   * If a {@code prefixDeclaration} does not contain an '=', the prefix will be resolved against the
   * provided {@code prefixMappings}.
   *
   *
   * @param prefixDeclarations - The prefixes declarations to use.
   * @param prefixMappings - The {@link List} of {@link Path}s that are or contain prefix mapping
   *        files.
   * @return the {@link Map} of {@link String} prefix keys and {@link String} name (IRI) values.
   * @throws PrefixMappingException when a prefix mapping cannot be made due to erroneous input.
   */
  Map<String, String> getNamespacePrefixes(@NonNull List<String> prefixDeclarations, @NonNull List<Path> prefixMappings)
      throws PrefixMappingException;
}
