package com.dq.engine.model;

/**
 * Everything the engine emits for one (rule, record) pair: either a verdict
 * ({@link RuleResult}) or a failure ({@link RuleError}).
 *
 * <p>One sealed type rather than two output channels, so results and errors arrive in the
 * order they happened and a caller cannot receive one and forget the other. Being sealed,
 * a {@code switch} over it needs no {@code default} and is checked for exhaustiveness:
 *
 * {@snippet :
 * switch (outcome) {
 *     case RuleResult r -> report.add(r);
 *     case RuleError e  -> alerts.raise(e);
 * }
 * }
 */
public sealed interface Outcome permits RuleResult, RuleError {

    String ruleId();

    String recordId();
}
