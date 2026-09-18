package com.dq.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DecisionMappingTest {

    @Nested
    @DisplayName("decide")
    class Decide {

        @Test
        @DisplayName("returns the mapped decision for a known value")
        void mapsKnownValue() {
            DecisionMapping mapping = DecisionMapping.builder()
                    .map("ok", Decision.VALID)
                    .map("bad", Decision.INVALID)
                    .otherwise(Decision.REVIEW);

            assertThat(mapping.decide("ok")).isEqualTo(Decision.VALID);
            assertThat(mapping.decide("bad")).isEqualTo(Decision.INVALID);
        }

        @Test
        @DisplayName("falls back for any unmapped value, so it is total")
        void fallsBackForUnknownValue() {
            DecisionMapping mapping = DecisionMapping.builder()
                    .map("blocked", Decision.INVALID)
                    .otherwise(Decision.NOT_APPLICABLE);

            assertThat(mapping.decide("ok")).isEqualTo(Decision.NOT_APPLICABLE);
            assertThat(mapping.decide("")).isEqualTo(Decision.NOT_APPLICABLE);
            assertThat(mapping.decide("something nobody anticipated"))
                    .isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("rejects a null value rather than guessing a decision")
        void rejectsNullValue() {
            DecisionMapping mapping = DecisionMapping.builder().otherwise(Decision.REVIEW);

            assertThatNullPointerException().isThrownBy(() -> mapping.decide(null));
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("copies the case table, so a later mutation cannot change the rule")
        void copiesCases() {
            Map<String, Decision> mutable = new HashMap<>();
            mutable.put("ok", Decision.VALID);

            DecisionMapping mapping = new DecisionMapping(mutable, Decision.REVIEW);
            mutable.put("ok", Decision.INVALID);

            assertThat(mapping.decide("ok")).isEqualTo(Decision.VALID);
        }
    }
}
