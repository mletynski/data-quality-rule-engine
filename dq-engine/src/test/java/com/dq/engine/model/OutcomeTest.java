package com.dq.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class OutcomeTest {

    /**
     * The switch below has no {@code default} branch on purpose. Because {@code Outcome} is
     * sealed the compiler proves the two cases are exhaustive, so adding a third kind of
     * outcome later breaks this compilation instead of being silently ignored at runtime.
     */
    @Test
    @DisplayName("a sealed Outcome can be handled with an exhaustive switch, no default branch")
    void switchIsExhaustive() {
        List<Outcome> outcomes = List.of(
                new RuleResult("vatFormat", "r2", Decision.INVALID, Severity.ERROR, "bad",
                        List.of(new FieldRead("vatId", "FR22"))),
                RuleError.from("vatFormat", "r2", new IllegalStateException("boom"), List.of()));

        List<String> rendered = outcomes.stream()
                .map(outcome -> switch (outcome) {
                    case RuleResult result -> "result:" + result.decision();
                    case RuleError error -> "error:" + error.exceptionType();
                })
                .toList();

        assertThat(rendered).containsExactly("result:INVALID", "error:IllegalStateException");
    }

    @Test
    @DisplayName("provenance is copied, so mutating the caller's list cannot change an emitted result")
    void provenanceIsDefensivelyCopied() {
        List<FieldRead> reads = new ArrayList<>();
        reads.add(new FieldRead("vatId", "FR22"));

        RuleResult result = new RuleResult("vatFormat", "r2", Decision.INVALID, Severity.ERROR, "bad", reads);

        reads.add(new FieldRead("country", "FR"));

        assertThat(result.provenance()).hasSize(1);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> result.provenance().add(new FieldRead("iban", "X")));
    }

    @Test
    @DisplayName("RuleError unwraps to the root cause and tolerates a null message")
    void errorUnwrapsRootCause() {
        Exception wrapped = new RuntimeException("outer", new IllegalArgumentException());

        RuleError error = RuleError.from("vatFormat", "r2", wrapped, List.of());

        assertThat(error.exceptionType()).isEqualTo("IllegalArgumentException");
        assertThat(error.message()).isEmpty();
    }
}
