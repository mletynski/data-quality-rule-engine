package com.dq.engine.model;

import java.io.Serial;
import java.util.List;

/**
 * Thrown by {@link RuleLogic} when evaluation fails, carrying the fields already read so
 * that the resulting {@link RuleError} can say not just that a rule failed but on what.
 */
public class RuleEvaluationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<FieldRead> provenance;

    public RuleEvaluationException(String message, Throwable cause, List<FieldRead> provenance) {
        super(message, cause);
        this.provenance = List.copyOf(provenance);
    }

    public List<FieldRead> provenance() {
        return provenance;
    }
}
