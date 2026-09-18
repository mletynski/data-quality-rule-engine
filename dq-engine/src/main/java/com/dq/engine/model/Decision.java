package com.dq.engine.model;

/**
 * The verdict a rule reached for a record.
 *
 * <p>Rule logic never produces one of these directly: it produces a {@code String}, and a
 * {@link DecisionMapping} translates that into a decision.
 */
public enum Decision {

    VALID,

    INVALID,

    /** The rule cannot decide on its own; a human must look. */
    REVIEW,

    /** The rule ran but does not apply. Deliberately distinct from {@link #VALID}. */
    NOT_APPLICABLE
}
