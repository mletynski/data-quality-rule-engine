package com.dq.engine.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Builds a {@link Rule}. Eight components, two of them enums and two collections, so
 * positional arguments would be a trap.
 *
 * <p>Status defaults to {@link RuleStatus#DRAFT}, so a rule built without saying otherwise
 * does not run.
 */
public final class RuleBuilder {

    private final String id;
    private String label;
    private RuleStatus status = RuleStatus.DRAFT;
    private Severity severity = Severity.ERROR;
    private String country;
    private final Set<String> categories = new LinkedHashSet<>();
    private RuleLogic logic;
    private DecisionMapping mapping;

    RuleBuilder(String id) {
        this.id = id;
        this.label = id;
    }

    public RuleBuilder label(String label) {
        this.label = label;
        return this;
    }

    public RuleBuilder status(RuleStatus status) {
        this.status = status;
        return this;
    }

    public RuleBuilder severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    /** Limits the rule to one country. Omit for all countries. */
    public RuleBuilder country(String country) {
        this.country = country;
        return this;
    }

    public RuleBuilder categories(String... categories) {
        this.categories.addAll(Set.of(categories));
        return this;
    }

    public RuleBuilder logic(RuleLogic logic) {
        this.logic = logic;
        return this;
    }

    public RuleBuilder mapping(DecisionMapping mapping) {
        this.mapping = mapping;
        return this;
    }

    public Rule build() {
        return new Rule(id, label, status, severity, country, categories, logic, mapping);
    }
}
