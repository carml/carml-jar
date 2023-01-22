package io.carml.jar.runner.prefix;

import static io.carml.jar.runner.TestApplication.getTestSourcePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultNamespacePrefixMapperTest {

  private static final Path TEST_PATH = getTestSourcePath(Paths.get("prefix", "default-namespace-prefix-mapper"));

  private NamespacePrefixMapper namespacePrefixMapper;

  @BeforeEach
  void beforeEach() {
    var objectMapper = new ObjectMapper();
    var yamlMapper = new YAMLMapper();
    namespacePrefixMapper = new DefaultNamespacePrefixMapper(objectMapper, yamlMapper);
  }

  @Test
  void givenNoPrefixDeclarationsNorPrefixMapping_whenGetNamespacePrefixes_thenReturnEmptyNamespaces() {
    // Given
    List<String> prefixDeclarations = List.of();
    List<Path> prefixMappings = List.of();

    // When
    var namespaces = namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, prefixMappings);

    // Then
    assertThat(namespaces.isEmpty(), is(true));
  }

  @Test
  void givenPrefixDeclarationsButNoPrefixMapping_whenGetNamespacePrefixes_thenReturnNamespacesFromDefaultMapping() {
    // Given
    var prefixDeclarations = List.of("skos", "foaf");

    // When
    var namespaces = namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, List.of());

    // Then
    assertThat(namespaces.size(), is(2));
    assertThat(namespaces, hasEntry("skos", "http://www.w3.org/2004/02/skos/core#"));
    assertThat(namespaces, hasEntry("foaf", "http://xmlns.com/foaf/0.1/"));
  }

  @Test
  void givenMixedPrefixDeclarationsButNoPrefixMapping_whenGetNamespacePrefixes_thenReturnExpectedNamespaces() {
    // Given
    var prefixDeclarations = List.of("skos", "foaf", "foo=http://foo.org/", "fooBarBaz");

    // When
    var namespaces = namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, List.of());

    // Then
    assertThat(namespaces.size(), is(3));
    assertThat(namespaces, hasEntry("skos", "http://www.w3.org/2004/02/skos/core#"));
    assertThat(namespaces, hasEntry("foaf", "http://xmlns.com/foaf/0.1/"));
    assertThat(namespaces, hasEntry("foo", "http://foo.org/"));
  }

  @Test
  void givenPrefixDeclarationsAndJsonPrefixMapping_whenGetNamespacePrefixes_thenReturnNamespacesFromMapping() {
    // Given
    var prefixDeclarations = List.of("foo", "bar");
    var prefixMappings = List.of(TEST_PATH.resolve("mappings")
        .resolve("prefixes.json"));

    // When
    var namespaces = namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, prefixMappings);

    // Then
    assertThat(namespaces.size(), is(2));
    assertThat(namespaces, hasEntry("foo", "http://foo.org/"));
    assertThat(namespaces, hasEntry("bar", "http://bar.org/"));
  }

  @Test
  void givenPrefixDeclarationsAndYamlPrefixMapping_whenGetNamespacePrefixes_thenReturnNamespacesFromMapping() {
    // Given
    var prefixDeclarations = List.of("bar", "baz");
    var prefixMappings = List.of(TEST_PATH.resolve("mappings")
        .resolve("prefixes.yaml"));

    // When
    var namespaces = namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, prefixMappings);

    // Then
    assertThat(namespaces.size(), is(2));
    assertThat(namespaces, hasEntry("bar", "http://bar.org/"));
    assertThat(namespaces, hasEntry("baz", "http://baz.org/"));
  }

  @Test
  void givenPrefixDeclarationsAndMultiplePrefixMappings_whenGetNamespacePrefixes_thenReturnNamespacesFromMappings() {
    // Given
    var prefixDeclarations = List.of("foo", "bar", "baz");
    var prefixMappings = List.of(TEST_PATH.resolve("mappings"));

    // When
    var namespaces = namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, prefixMappings);

    // Then
    assertThat(namespaces.size(), is(3));
    assertThat(namespaces, hasEntry("foo", "http://foo.org/"));
    assertThat(namespaces, hasEntry("bar", "http://bar.org/"));
    assertThat(namespaces, hasEntry("baz", "http://baz.org/"));
  }

  @Test
  void givenPrefixDeclarationsAndPrefixMappingWithoutExtension_whenGetNamespacePrefixes_thenThrowException() {
    // Given
    var prefixDeclarations = List.of("foo", "bar");
    var prefixMappings = List.of(TEST_PATH.resolve("errors")
        .resolve("prefixes-no-extension"));

    // When
    var prefixMappingException = assertThrows(PrefixMappingException.class,
        () -> namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, prefixMappings));

    // Then
    assertThat(prefixMappingException.getMessage(), startsWith("Could not determine prefix mapping file format for"));
  }

  @Test
  void givenPrefixDeclarationsAndIllFormattedPrefixMapping_whenGetNamespacePrefixes_thenThrowException() {
    // Given
    var prefixDeclarations = List.of("foo", "bar");
    var prefixMappings = List.of(TEST_PATH.resolve("errors")
        .resolve("prefixes-ill-formatted.json"));

    // When
    var prefixMappingException = assertThrows(PrefixMappingException.class,
        () -> namespacePrefixMapper.getNamespacePrefixes(prefixDeclarations, prefixMappings));

    // Then
    assertThat(prefixMappingException.getMessage(), startsWith("Could not parse prefix mapping file"));
  }
}
