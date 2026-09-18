package com.dq.engine;

import com.dq.engine.model.Rule;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Builds a {@link ValidationEngine}, settling every question that does not need a record.
 *
 * <p>A rule rejected here is never seen again, so a DRAFT rule or one the caller excluded
 * costs nothing per record rather than being re-rejected a million times.
 */
public final class ValidationEngineBuilder {

    private final List<Rule> catalog;
    private Predicate<Rule> selection = rule -> true;
    private int batchSize = 1_000;

    ValidationEngineBuilder(List<Rule> catalog) {
        this.catalog = List.copyOf(Objects.requireNonNull(catalog, "catalog"));
    }

    /**
     * Restricts the run by status, category, id or anything else about a rule. Compose
     * several with {@link Predicate#and}, {@code or} and {@code negate}.
     *
     * <p>It can never widen the run: {@link #build()} intersects it with RELEASED.
     */
    public ValidationEngineBuilder select(Predicate<Rule> selection) {
        this.selection = Objects.requireNonNull(selection, "selection");
        return this;
    }

    /** How many records between {@link OutcomeHandler#flush()} calls. */
    public ValidationEngineBuilder batchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1 but was " + batchSize);
        }
        this.batchSize = batchSize;
        return this;
    }

    /**
     * The RELEASED check is applied after the caller's selection and out of their reach, so
     * no predicate can cause a DRAFT or DEPRECATED rule to run.
     */
    public ValidationEngine build() {
        List<Rule> selected = catalog.stream()
                .filter(Rule::isReleased)
                .filter(selection)
                .toList();

        return new ValidationEngine(selected, batchSize);
    }
}
