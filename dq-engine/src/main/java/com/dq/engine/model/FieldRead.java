package com.dq.engine.model;

import java.util.Objects;

/**
 * One field a rule read, and the value it saw. A list of these is the provenance that
 * explains a decision.
 *
 * @param value the value observed, or null if the field was absent. Absent and
 *              present-but-null are not distinguished; no rule needs the difference.
 */
public record FieldRead(String field, Object value) {

    public FieldRead {
        Objects.requireNonNull(field, "field");
    }
}
