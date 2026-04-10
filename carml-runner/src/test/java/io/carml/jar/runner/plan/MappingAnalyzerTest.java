package io.carml.jar.runner.plan;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MappingAnalyzerTest {

  private static final String REACTIVE = "reactive";

  private static final String IN_PROCESS_DB = "in-process-db";

  @Nested
  class RecommendEvaluator {

    // --- Not DuckDB compatible ---

    @Test
    void notCompatible_noJoins_noRows() {
      assertThat(recommend(false, false, null, null), is(REACTIVE));
    }

    @Test
    void notCompatible_noJoins_smallRows() {
      assertThat(recommend(false, false, 100L, null), is(REACTIVE));
    }

    @Test
    void notCompatible_noJoins_largeRows() {
      assertThat(recommend(false, false, 1_000_000L, null), is(REACTIVE));
    }

    @Test
    void notCompatible_withJoins_largeRows() {
      assertThat(recommend(false, true, 1_000_000L, 1_000_000L), is(REACTIVE));
    }

    // --- Compatible, no joins ---

    @Test
    void compatible_noJoins_noRows() {
      assertThat(recommend(true, false, null, null), is(REACTIVE));
    }

    @Test
    void compatible_noJoins_smallRows() {
      assertThat(recommend(true, false, 50L, null), is(REACTIVE));
    }

    @Test
    void compatible_noJoins_atThreshold() {
      assertThat(recommend(true, false, 100_000L, null), is(REACTIVE));
    }

    @Test
    void compatible_noJoins_aboveThreshold() {
      assertThat(recommend(true, false, 100_001L, null), is(IN_PROCESS_DB));
    }

    @Test
    void compatible_noJoins_largeRows() {
      assertThat(recommend(true, false, 10_000_000L, null), is(IN_PROCESS_DB));
    }

    // --- Compatible, with joins, own source size ---

    @Test
    void compatible_withJoins_unknownRows_unknownParent() {
      assertThat(recommend(true, true, null, null), is(IN_PROCESS_DB));
    }

    @Test
    void compatible_withJoins_smallRows_noParentInfo() {
      assertThat(recommend(true, true, 50L, null), is(IN_PROCESS_DB));
    }

    @Test
    void compatible_withJoins_smallRows_smallParent() {
      assertThat(recommend(true, true, 50L, 50L), is(REACTIVE));
    }

    @Test
    void compatible_withJoins_atThreshold_smallParent() {
      assertThat(recommend(true, true, 100_000L, 50L), is(REACTIVE));
    }

    @Test
    void compatible_withJoins_aboveThreshold_smallParent() {
      assertThat(recommend(true, true, 100_001L, 50L), is(IN_PROCESS_DB));
    }

    @Test
    void compatible_withJoins_largeRows_smallParent() {
      assertThat(recommend(true, true, 1_000_000L, 50L), is(IN_PROCESS_DB));
    }

    // --- Compatible, with joins, parent source size ---

    @Test
    void compatible_withJoins_smallRows_largeParent() {
      assertThat(recommend(true, true, 9L, 1_000_000L), is(IN_PROCESS_DB));
    }

    @Test
    void compatible_withJoins_smallRows_parentAtThreshold() {
      assertThat(recommend(true, true, 9L, 100_000L), is(REACTIVE));
    }

    @Test
    void compatible_withJoins_smallRows_parentAboveThreshold() {
      assertThat(recommend(true, true, 9L, 100_001L), is(IN_PROCESS_DB));
    }

    @Test
    void compatible_withJoins_smallRows_unknownParent() {
      // Unknown parent size with small own source — prefer DuckDB for safety
      assertThat(recommend(true, true, 9L, null), is(IN_PROCESS_DB));
    }

    @Test
    void compatible_withJoins_unknownRows_largeParent() {
      assertThat(recommend(true, true, null, 1_000_000L), is(IN_PROCESS_DB));
    }

    @Test
    void compatible_withJoins_unknownRows_smallParent() {
      // Own source unknown — prefer DuckDB for safety even if parent is small
      assertThat(recommend(true, true, null, 50L), is(IN_PROCESS_DB));
    }

    // --- Compatible, with joins, both large ---

    @Test
    void compatible_withJoins_largeRows_largeParent() {
      assertThat(recommend(true, true, 500_000L, 1_000_000L), is(IN_PROCESS_DB));
    }

    @Test
    void compatible_withJoins_largeRows_evenLargerParent() {
      assertThat(recommend(true, true, 100_001L, 999_999_999L), is(IN_PROCESS_DB));
    }

    // --- Edge cases ---

    @Test
    void compatible_noJoins_zeroRows() {
      assertThat(recommend(true, false, 0L, null), is(REACTIVE));
    }

    @Test
    void compatible_withJoins_zeroRows_zeroParent() {
      assertThat(recommend(true, true, 0L, 0L), is(REACTIVE));
    }

    @Test
    void compatible_withJoins_oneRow_oneParentRow() {
      assertThat(recommend(true, true, 1L, 1L), is(REACTIVE));
    }

    private static String recommend(boolean compatible, boolean hasJoins, Long estimatedRows, Long maxJoinRows) {
      return MappingAnalyzer.recommendEvaluator(compatible, hasJoins, estimatedRows, maxJoinRows);
    }
  }
}
