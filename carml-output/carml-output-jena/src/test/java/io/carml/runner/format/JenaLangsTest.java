package io.carml.runner.format;

import static io.carml.runner.format.JenaLangs.determineLang;
import static io.carml.runner.format.JenaLangs.supportsGraphs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.carml.runner.CarmlJarException;
import java.util.stream.Stream;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JenaLangsTest {

  static Stream<Arguments> rdfFormatLangArgs() {
    return Stream.of(//
        Arguments.of("ttl", Lang.TURTLE), Arguments.of("nq", Lang.NQUADS), Arguments.of("nt", Lang.NTRIPLES),
        Arguments.of("n3", Lang.N3));
  }

  @ParameterizedTest
  @MethodSource("rdfFormatLangArgs")
  void givenRdfFormat_whenDetermineLang_thenReturnExpectedLang(String rdfFormat, Lang expectedLang) {
    // Given
    // When
    var lang = determineLang(rdfFormat);

    // Then
    assertThat(lang, is(expectedLang));
  }

  @Test
  void givenUnsupportedRdfFormat_whenDetermineLang_thenThrowException() {
    // Given
    var unsupportedRdfFormat = "foo";

    // When
    var carmlJarException = assertThrows(CarmlJarException.class, () -> determineLang(unsupportedRdfFormat));

    // Then
    assertThat(carmlJarException.getMessage(), is("Unsupported RDF Format reference specified: 'foo'."));
  }

  static Stream<Arguments> langSupportsGraphsArgs() {
    return Stream.of(//
        Arguments.of(Lang.TURTLE, false), //
        Arguments.of(Lang.TRIG, true));
  }

  @ParameterizedTest
  @MethodSource("langSupportsGraphsArgs")
  void givenLang_whenSupportsGraphs_thenReturnExpectedSupportsGraphs(Lang lang, boolean expectedSupportsGraphs) {
    // Given
    // When
    var supportsGraphs = supportsGraphs(lang);

    // Then
    assertThat(supportsGraphs, is(expectedSupportsGraphs));
  }
}
