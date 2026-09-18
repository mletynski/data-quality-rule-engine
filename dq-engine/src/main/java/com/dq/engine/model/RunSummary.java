package com.dq.engine.model;

import java.util.Map;

/**
 * What happened in a run: how many results, how many errors, and a breakdown of the results
 * by decision and by severity.
 *
 * <p>Every field is a counter, so the summary costs the same for ten records as for ten
 * million — which is why it is the one thing {@code validate} returns by value.
 *
 * @param byDecision every {@link Decision} is present, zero-valued if unseen
 * @param bySeverity every {@link Severity} is present, zero-valued if unseen
 */
public record RunSummary(
        long resultCount,
        long errorCount,
        Map<Decision, Long> byDecision,
        Map<Severity, Long> bySeverity) {

    public RunSummary {
        byDecision = Map.copyOf(byDecision);
        bySeverity = Map.copyOf(bySeverity);
    }
}
