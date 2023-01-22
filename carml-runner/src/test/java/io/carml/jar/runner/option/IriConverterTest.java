package io.carml.jar.runner.option;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IriConverterTest {

  @Test
  void givenInvalidIriString_whenConvert_thenThrowException() {
    // Given
    var iriString = "foo.bar/";

    // When
    var exception = assertThrows(Exception.class, () -> new IriConverter().convert(iriString));

    // Then
    assertThat(exception.getMessage(), is("`foo.bar/` is not a valid IRI"));
  }

  @Test
  void givenValidIriString_whenConvert_thenReturnIri() throws Exception {
    // Given
    var iriString = "https://foo.bar/";

    // When
    var iri = new IriConverter().convert(iriString);

    // Then
    assertThat(iri, is(iri("https://foo.bar/")));
  }
}
