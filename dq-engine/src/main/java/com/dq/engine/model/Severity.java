package com.dq.engine.model;

/**
 * How much a rule's verdict matters. Belongs to the rule and is copied onto every
 * {@link RuleResult}, so a caller can summarise by severity without consulting the catalog.
 *
 * <p>Independent of {@link Decision}: a {@code WARNING} rule returning {@code INVALID}
 * means "wrong, but not blocking". The engine never infers one from the other.
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO
}
