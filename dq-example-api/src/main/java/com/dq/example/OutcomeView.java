package com.dq.example;

import com.dq.engine.model.FieldRead;
import com.dq.engine.model.Outcome;
import com.dq.engine.model.RuleError;
import com.dq.engine.model.RuleResult;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The HTTP wire format for one outcome.
 *
 * <p>In the host, not the library: field names and the {@code type} discriminator are this
 * host's contract with its clients. Annotating {@code RuleResult} would freeze one host's
 * wire format into the library.
 *
 * <p>The switch has no {@code default} — {@link Outcome} is sealed, so a third kind of
 * outcome would break this compilation rather than serialise wrongly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutcomeView(
        String type,
        String ruleId,
        String recordId,
        String decision,
        String severity,
        String computedValue,
        String exceptionType,
        String message,
        List<FieldView> provenance) {

    public static OutcomeView of(Outcome outcome) {
        return switch (outcome) {
            case RuleResult result -> new OutcomeView("result",
                    result.ruleId(), result.recordId(),
                    result.decision().name(), result.severity().name(), result.computedValue(),
                    null, null,
                    toViews(result.provenance()));

            case RuleError error -> new OutcomeView("error",
                    error.ruleId(), error.recordId(),
                    null, null, null,
                    error.exceptionType(), error.message(),
                    toViews(error.provenance()));
        };
    }

    private static List<FieldView> toViews(List<FieldRead> reads) {
        return reads.stream().map(read -> new FieldView(read.field(), read.value())).toList();
    }
}
