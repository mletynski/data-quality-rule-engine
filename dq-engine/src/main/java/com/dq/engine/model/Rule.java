package com.dq.engine.model;

import java.util.Objects;
import java.util.Set;

/**
 * A named data-quality check: metadata, logic, and the mapping from the logic's value to a
 * {@link Decision}.
 *
 * @param country    the country this rule applies to, or null for all countries
 * @param categories free-form tags used for selection
 */
public record Rule(
        String id,
        String label,
        RuleStatus status,
        Severity severity,
        String country,
        Set<String> categories,
        RuleLogic logic,
        DecisionMapping mapping) {

    public Rule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(mapping, "mapping");
        categories = Set.copyOf(categories);
    }

    public boolean isReleased() {
        return status == RuleStatus.RELEASED;
    }

    /**
     * A rule with no country applies everywhere — including to records that have no country
     * at all, which are exactly the ones a quality run exists to find.
     */
    public boolean appliesTo(String recordCountry) {
        return country == null || country.equals(recordCountry);
    }

    public static RuleBuilder builder(String id) {
        return new RuleBuilder(id);
    }
}
