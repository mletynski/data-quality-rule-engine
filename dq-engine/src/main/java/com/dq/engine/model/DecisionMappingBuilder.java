package com.dq.engine.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a {@link DecisionMapping}.
 *
 * <p>{@link #otherwise} is the terminal call, so a mapping without a fallback is
 * unrepresentable rather than merely rejected — which is what makes
 * {@link DecisionMapping#decide} total.
 */
public final class DecisionMappingBuilder {

    private final Map<String, Decision> cases = new LinkedHashMap<>();

    DecisionMappingBuilder() {
    }

    public DecisionMappingBuilder map(String value, Decision decision) {
        cases.put(Objects.requireNonNull(value, "value"),
                Objects.requireNonNull(decision, "decision"));
        return this;
    }

    /** Sets the fallback and builds the mapping. */
    public DecisionMapping otherwise(Decision fallback) {
        return new DecisionMapping(cases, fallback);
    }
}
