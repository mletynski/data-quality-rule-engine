package com.dq.engine.model;

import java.util.List;
import java.util.Objects;

/**
 * A rule that failed on a record: bad logic, a bad record, a missing field.
 *
 * <p>A failure is a value, not an exception, so one broken rule cannot stop the batch. No
 * {@code Throwable} is stored — it would retain a reference to the record that created it,
 * and a rule failing on a million records would pin a million record graphs in memory.
 *
 * @param provenance the fields read before the failure
 */
public record RuleError(
        String ruleId,
        String recordId,
        String exceptionType,
        String message,
        List<FieldRead> provenance) implements Outcome {

    public RuleError {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(exceptionType, "exceptionType");
        Objects.requireNonNull(message, "message");
        provenance = List.copyOf(provenance);
    }

    /** Unwraps to the root cause, because SpEL wraps the failure that actually explains things. */
    public static RuleError from(String ruleId, String recordId, Throwable failure,
                                 List<FieldRead> provenance) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? "" : root.getMessage();
        return new RuleError(ruleId, recordId, root.getClass().getSimpleName(), message, provenance);
    }
}
