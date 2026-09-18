package com.dq.engine.model;

import java.util.List;
import java.util.Objects;

/**
 * What a {@link RuleLogic} produced for one record: the computed value, and the fields it
 * read to reach it.
 *
 * <p>Note what is absent: a {@link Decision}. The mapping does that, so a rule author
 * cannot bypass it.
 */
public record Evaluation(String value, List<FieldRead> provenance) {

    public Evaluation {
        Objects.requireNonNull(value, "value");
        provenance = List.copyOf(provenance);
    }
}
