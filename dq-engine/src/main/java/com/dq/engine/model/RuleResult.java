package com.dq.engine.model;

import java.util.List;
import java.util.Objects;

/**
 * What a rule decided for a record, with enough provenance to explain it.
 *
 * @param computedValue the value the logic produced before mapping — without it, a
 *                      {@code REVIEW} from the mapping's default is indistinguishable from
 *                      one that was mapped explicitly
 * @param provenance    the fields the rule read, in read order; copied on construction
 */
public record RuleResult(
        String ruleId,
        String recordId,
        Decision decision,
        Severity severity,
        String computedValue,
        List<FieldRead> provenance) implements Outcome {

    public RuleResult {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(computedValue, "computedValue");
        provenance = List.copyOf(provenance);
    }
}
