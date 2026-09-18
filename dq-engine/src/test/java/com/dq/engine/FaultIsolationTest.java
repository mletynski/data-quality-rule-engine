package com.dq.engine;

import com.dq.engine.model.Decision;
import com.dq.engine.model.DecisionMapping;
import com.dq.engine.model.FieldRead;
import com.dq.engine.model.Outcome;
import com.dq.engine.model.Rule;
import com.dq.engine.model.RuleError;
import com.dq.engine.model.RuleStatus;
import com.dq.engine.model.RunSummary;
import com.dq.engine.spel.SpelRuleLogic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule can fail. When it does the engine records a located error, keeps the rest of the
 * batch running, and hands the caller both the results and the errors.
 */
class FaultIsolationTest {

    /** A rule whose logic throws — the "bad logic" failure mode. */
    private static Rule explodingRule() {
        return Rule.builder("explodes")
                .status(RuleStatus.RELEASED)
                .logic(record -> {
                    throw new IllegalStateException("rule author made a mistake");
                })
                .mapping(DecisionMapping.builder().otherwise(Decision.REVIEW))
                .build();
    }

    private static List<Rule> catalogPlusBrokenRule() {
        List<Rule> rules = new ArrayList<>(Fixtures.catalog());
        rules.add(explodingRule());
        return rules;
    }

    /**
     * The three good rules still produce all nine results, and the broken one produces
     * three located errors rather than ending the run.
     */
    @Test
    @DisplayName("a rule that throws on every record does not stop the batch")
    void oneBrokenRuleDoesNotStopTheBatch() {
        List<Outcome> outcomes = new ArrayList<>();
        RunSummary summary = ValidationEngine.builder(catalogPlusBrokenRule())
                .build()
                .validate(Fixtures.records().stream(), outcomes::add);

        assertThat(summary.resultCount()).isEqualTo(9);
        assertThat(summary.errorCount()).isEqualTo(3);

        assertThat(errors(outcomes))
                .hasSize(3)
                .allSatisfy(error -> {
                    assertThat(error.ruleId()).isEqualTo("explodes");
                    assertThat(error.exceptionType()).isEqualTo("IllegalStateException");
                    assertThat(error.message()).isEqualTo("rule author made a mistake");
                })
                .extracting(RuleError::recordId)
                .containsExactly("r1", "r2", "r3");
    }

    @Test
    @DisplayName("a failing expression reports the fields it had already read")
    void errorsCarryPartialProvenance() {
        Rule rule = Rule.builder("readsThenFails")
                .status(RuleStatus.RELEASED)
                .logic(SpelRuleLogic.of("legalName.noSuchMethod() ? 'ok' : 'bad'"))
                .mapping(DecisionMapping.builder().otherwise(Decision.REVIEW))
                .build();

        List<Outcome> outcomes = new ArrayList<>();
        ValidationEngine.builder(List.of(rule))
                .build()
                .validate(Stream.of(Fixtures.r1()), outcomes::add);

        RuleError error = errors(outcomes).getFirst();
        assertThat(error.provenance()).containsExactly(new FieldRead("legalName", "ACME GmbH"));
        assertThat(error.message()).isNotEmpty();
    }

    /** {@code vatId != null} evaluates to a Boolean, not to a value string. */
    @Test
    @DisplayName("a rule producing a non-String value is an error, not a silent default")
    void wrongReturnTypeIsReportedNotCoerced() {
        Rule rule = Rule.builder("returnsBoolean")
                .status(RuleStatus.RELEASED)
                .logic(SpelRuleLogic.of("vatId != null"))
                .mapping(DecisionMapping.builder().map("true", Decision.VALID).otherwise(Decision.REVIEW))
                .build();

        List<Outcome> outcomes = new ArrayList<>();
        ValidationEngine.builder(List.of(rule))
                .build()
                .validate(Stream.of(Fixtures.r1()), outcomes::add);

        assertThat(errors(outcomes)).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("must produce a String"));
    }

    private static List<RuleError> errors(List<Outcome> outcomes) {
        return outcomes.stream().filter(RuleError.class::isInstance).map(RuleError.class::cast).toList();
    }
}
