package com.dq.engine;

import com.dq.engine.model.DataRecord;
import com.dq.engine.model.Decision;
import com.dq.engine.model.Evaluation;
import com.dq.engine.model.FieldRead;
import com.dq.engine.model.Rule;
import com.dq.engine.model.RuleError;
import com.dq.engine.model.RuleEvaluationException;
import com.dq.engine.model.RuleResult;
import com.dq.engine.model.RunSummary;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Runs a catalog of rules over a stream of records.
 *
 * {@snippet :
 * ValidationEngine engine = ValidationEngine.builder(rules)
 *         .select(rule -> rule.categories().contains("tax"))
 *         .batchSize(500)
 *         .build();
 *
 * RunSummary summary = engine.validate(records, outcome -> writer.write(outcome));
 * }
 *
 * <p>Selection happens in two places, and which place is not a choice: status and category
 * are settled once by {@link ValidationEngineBuilder}, while country scope is the only
 * question that needs a record and so is the only one asked per record.
 *
 * <p>Immutable and thread-safe; one engine can serve every request of a web application.
 * See DESIGN.md for the reasoning behind the API shape and the fault-isolation rules.
 */
public final class ValidationEngine {

    private final List<Rule> rules;
    private final int batchSize;

    ValidationEngine(List<Rule> rules, int batchSize) {
        this.rules = rules;
        this.batchSize = batchSize;
    }

    public static ValidationEngineBuilder builder(List<Rule> catalog) {
        return new ValidationEngineBuilder(catalog);
    }

    /**
     * Validates a stream of records, pushing every outcome to {@code outcomes} as it is
     * produced. The stream is consumed lazily and closed at the end, so a file-, socket- or
     * cursor-backed source is held one record at a time.
     *
     * @return counters for the run; never the outcomes themselves
     */
    public RunSummary validate(Stream<DataRecord> records, OutcomeHandler outcomes) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(outcomes, "outcomes");

        RunCounters counters = new RunCounters();

        try (Stream<DataRecord> stream = records) {
            Iterator<DataRecord> iterator = stream.iterator();
            int inBatch = 0;

            while (iterator.hasNext()) {
                validateRecord(iterator.next(), outcomes, counters);

                if (++inBatch == batchSize) {
                    outcomes.flush();
                    inBatch = 0;
                }
            }
        }
        outcomes.flush();

        return counters.toSummary();
    }

    private void validateRecord(DataRecord record, OutcomeHandler outcomes, RunCounters counters) {
        String country = record.country().orElse(null);

        for (Rule rule : rules) {
            if (rule.appliesTo(country)) {
                evaluate(rule, record, outcomes, counters);
            }
        }
    }

    /** Runs one rule against one record, turning any failure into a {@link RuleError}. */
    private void evaluate(Rule rule, DataRecord record, OutcomeHandler outcomes, RunCounters counters) {
        try {
            Evaluation evaluation = rule.logic().evaluate(record);
            Decision decision = rule.mapping().decide(evaluation.value());

            counters.countResult(decision, rule.severity());
            outcomes.handle(new RuleResult(rule.id(), record.id(), decision, rule.severity(),
                    evaluation.value(), evaluation.provenance()));

        } catch (RuntimeException failure) {
            List<FieldRead> readSoFar = failure instanceof RuleEvaluationException evaluationFailure
                    ? evaluationFailure.provenance()
                    : List.of();

            counters.countError();
            outcomes.handle(RuleError.from(rule.id(), record.id(), failure, readSoFar));
        }
    }
}
