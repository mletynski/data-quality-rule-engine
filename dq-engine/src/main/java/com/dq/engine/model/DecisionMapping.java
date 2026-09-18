package com.dq.engine.model;

import java.util.Map;
import java.util.Objects;

/**
 * Translates a rule's computed value into a {@link Decision}. This is what keeps policy as
 * data: reclassifying "bad VAT id" from {@code INVALID} to {@code REVIEW} is a row update,
 * not a release.
 *
 * {@snippet :
 * DecisionMapping.builder()
 *         .map("blocked", Decision.INVALID)
 *         .otherwise(Decision.NOT_APPLICABLE);
 * }
 *
 * <p>{@link #decide} is total — anything unmapped falls through to the fallback — so the
 * engine has no "unmapped value" error path at all.
 */
public record DecisionMapping(Map<String, Decision> cases, Decision otherwise) {

    public DecisionMapping {
        Objects.requireNonNull(otherwise, "otherwise");
        cases = Map.copyOf(cases);
    }

    /** @throws NullPointerException if {@code value} is null, which is a rule failure */
    public Decision decide(String value) {
        Objects.requireNonNull(value, "computed value");
        return cases.getOrDefault(value, otherwise);
    }

    public static DecisionMappingBuilder builder() {
        return new DecisionMappingBuilder();
    }
}
