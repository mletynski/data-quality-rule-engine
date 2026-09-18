package com.dq.example;

import com.dq.engine.model.RunSummary;

import java.util.Map;
import java.util.stream.Collectors;

/** The HTTP wire format for a run summary. */
public record SummaryView(
        String type,
        long resultCount,
        long errorCount,
        Map<String, Long> byDecision,
        Map<String, Long> bySeverity) {

    public static SummaryView of(RunSummary summary) {
        return new SummaryView("summary",
                summary.resultCount(),
                summary.errorCount(),
                rename(summary.byDecision()),
                rename(summary.bySeverity()));
    }

    private static <E extends Enum<E>> Map<String, Long> rename(Map<E, Long> counts) {
        return counts.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
    }
}
