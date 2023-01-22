package io.carml.jar.runner.option;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.Test;

class Rdf4jOutputRdfFormatProviderTest {

  @Test
  void givenProvider_whenRdfFormats_thenReturnRdfFormats() {
    // Given
    var rdf4jOutputRdfFormatProvider = new Rdf4jOutputRdfFormatProvider();

    // When
    var rdfFormats = rdf4jOutputRdfFormatProvider.rdfFormats();

    // Then
    assertThat(rdfFormats, hasItem("nq"));
  }
}
