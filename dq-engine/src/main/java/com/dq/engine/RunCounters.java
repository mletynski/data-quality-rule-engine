package com.dq.engine;

import com.dq.engine.model.Decision;
import com.dq.engine.model.RunSummary;
import com.dq.engine.model.Severity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tallies what a single run produced. Created inside {@code validate}, so the engine itself
 * holds no mutable state and stays safe to share between threads.
 *
 * <p>Every decision and severity starts at zero, so the summary always carries a full
 * breakdown and a caller never has to tell "zero" apart from "absent".
 */
final class RunCounters {

    private final Map<Decision, Long> byDecision = new EnumMap<>(Decision.class);
    private final Map<Severity, Long> bySeverity = new EnumMap<>(Severity.class);

    private long resultCount;
    private long errorCount;

    RunCounters() {
        for (Decision decision : Decision.values()) {
            byDecision.put(decision, 0L);
        }
        for (Severity severity : Severity.values()) {
            bySeverity.put(severity, 0L);
        }
    }

    void countResult(Decision decision, Severity severity) {
        resultCount++;
        byDecision.merge(decision, 1L, Long::sum);
        bySeverity.merge(severity, 1L, Long::sum);
    }

    void countError() {
        errorCount++;
    }

    RunSummary toSummary() {
        return new RunSummary(resultCount, errorCount, byDecision, bySeverity);
    }
}
