package io.carml.jar.runner.prefix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.carml.jar.runner.input.FilePathResolver;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class DefaultNamespacePrefixMapper implements NamespacePrefixMapper {

  private static final Logger LOG = LogManager.getLogger();

  private enum PrefixMappingFormat {
    JSON, YAML
  }

  private static final String PREFIX_CC_MAPPING_FILE = "/prefix/prefix.cc.context.json";

  private final ObjectMapper objectMapper;

  private final YAMLMapper yamlMapper;

  private Map<String, String> storedPrefixMappings;

  public DefaultNamespacePrefixMapper(ObjectMapper objectMapper, YAMLMapper yamlMapper) {
    this.objectMapper = objectMapper;
    this.yamlMapper = yamlMapper;
    this.storedPrefixMappings = null;
  }

  /**
   * <p>
   * Maps {@code prefixDeclaration}s to a {@link Map} of {@link String} prefix keys and {@link String}
   * name (IRI) values to be used for namespace prefix mappings in RDF serializations.
   * </p>
   *
   * <p>
   * A {@code prefixDeclaration} {@link String} will be treated as an inline prefix declaration when
   * it contains an '='. For, example: {@code "ex=http://example.com/"}.
   * </p>
   *
   * <p>
   * If a {@code prefixDeclaration} does not contain an '=', the prefix will be resolved against the
   * provided {@code prefixMappings}.<br>
   * If no {@code prefixMappings} are provided, the default packaged prefix mapping (from
   * <a href="https://prefix.cc">prefix.cc</a>) will be used.
   * </p>
   *
   * @param prefixDeclarations - The prefixes declarations to use.
   * @param prefixMappings - The {@link List} of {@link Path}s that are or contain prefix mapping
   *        files.
   * @return the {@link Map} of {@link String} prefix keys and {@link String} name (IRI) values.
   * @throws PrefixMappingException when a prefix mapping cannot be made due to erroneous input.
   */
  @Override
  public Map<String, String> getNamespacePrefixes(@NonNull List<String> prefixDeclarations,
      @NonNull List<Path> prefixMappings) throws PrefixMappingException {
    if (prefixDeclarations.isEmpty()) {
      return Map.of();
    }

    return prefixDeclarations.stream()
        .map(prefixDeclaration -> handlePrefixDeclaration(prefixDeclaration, prefixMappings))
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private Entry<String, String> handlePrefixDeclaration(String prefixDeclaration, List<Path> prefixMappings) {
    return Prefixes.isInlinePrefixMapping(prefixDeclaration) ? handleInlinePrefixMapping(prefixDeclaration)
        : handlePrefixReference(prefixDeclaration, prefixMappings);
  }

  private Entry<String, String> handleInlinePrefixMapping(String inlinePrefixMapping) {
    var split = inlinePrefixMapping.split(Prefixes.INLINE_PREFIX_DECLARATION_SEPARATOR, 2);

    return Map.entry(split[0], split[1]);
  }

  private Entry<String, String> handlePrefixReference(String prefixReference, List<Path> prefixMappings) {
    if (storedPrefixMappings == null) {
      initializeStoredPrefixMapping(prefixMappings);
    }

    if (storedPrefixMappings.containsKey(prefixReference)) {
      return Map.entry(prefixReference, storedPrefixMappings.get(prefixReference));
    } else {
      LOG.warn("Prefix reference `{}` could not be resolved.", prefixReference);
      return null;
    }
  }

  private void initializeStoredPrefixMapping(List<Path> prefixMappings) {
    if (prefixMappings.isEmpty()) {
      try (var input = DefaultNamespacePrefixMapper.class.getResourceAsStream(PREFIX_CC_MAPPING_FILE)) {
        storedPrefixMappings = objectMapper.readValue(input, new TypeReference<>() {});
        return;
      } catch (IOException ioException) {
        throw new IllegalStateException("Could not process internally stored prefix.cc file.", ioException);
      }
    }

    storedPrefixMappings = new HashMap<>();
    FilePathResolver.resolveFilePaths(prefixMappings)
        .forEach(this::storePrefixMapping);
  }

  private void storePrefixMapping(Path path) {
    var format = determinePrefixMappingFormat(path);

    try {
      if (format == PrefixMappingFormat.JSON) {
        storedPrefixMappings.putAll(objectMapper.readValue(path.toFile(), new TypeReference<>() {}));
      } else if (format == PrefixMappingFormat.YAML) {
        storedPrefixMappings.putAll(yamlMapper.readValue(path.toFile(), new TypeReference<>() {}));
      }
    } catch (IOException ioException) {
      throw new PrefixMappingException(String.format("Could not parse prefix mapping file [%s].", path), ioException);
    }
  }

  private PrefixMappingFormat determinePrefixMappingFormat(Path path) {
    var fileExtension = FilenameUtils.getExtension(path.getFileName()
        .toString());

    switch (fileExtension) {
      case "json":
        return PrefixMappingFormat.JSON;
      case "yaml":
      case "yml":
        return PrefixMappingFormat.YAML;
      default:
        throw new PrefixMappingException(String.format(
            "Could not determine prefix mapping file format for [%s]. Files must end in '.json', '.yaml', or '.yml.'",
            path));
    }
  }
}
