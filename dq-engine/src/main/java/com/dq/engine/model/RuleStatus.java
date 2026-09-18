package com.dq.engine.model;

/** Lifecycle stage of a rule. Only {@link #RELEASED} rules are ever executed. */
public enum RuleStatus {
    DRAFT,
    RELEASED,
    DEPRECATED
}
