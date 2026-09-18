package com.dq.engine.model;

/**
 * The computation half of a rule: record in, value out.
 *
 * <p>It returns an {@link Evaluation} carrying a {@code String}, never a {@link Decision} —
 * the mapping does that — so a rule author cannot bypass the mapping even by accident.
 *
 * <p>The shipped implementation is {@link com.dq.engine.spel.SpelRuleLogic}. Implementations
 * must be thread-safe, and are free to throw: the engine turns any exception into a
 * {@link RuleError}.
 */
public interface RuleLogic {

    Evaluation evaluate(DataRecord record);
}
