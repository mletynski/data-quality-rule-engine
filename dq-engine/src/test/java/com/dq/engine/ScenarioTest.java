package com.dq.engine;

import com.dq.engine.model.Decision;
import com.dq.engine.model.DecisionMapping;
import com.dq.engine.model.FieldRead;
import com.dq.engine.model.Outcome;
import com.dq.engine.model.Rule;
import com.dq.engine.model.RuleResult;
import com.dq.engine.model.RuleStatus;
import com.dq.engine.model.RunSummary;
import com.dq.engine.model.Severity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scenario from section 5 of the task, asserted cell by cell.
 *
 * <pre>
 * record | countryBlocked  | vatFormat          | ibanFormat
 * -------|-----------------|--------------------|----------------------
 * r1     | NOT_APPLICABLE  | VALID              | VALID
 * r2     | NOT_APPLICABLE  | INVALID (len 4)    | VALID
 * r3     | INVALID         | INVALID (no vatId) | INVALID (empty iban)
 * </pre>
 */
class ScenarioTest {

    private List<Outcome> outcomes;
    private RunSummary summary;

    @BeforeEach
    void runTheScenario() {
        outcomes = new ArrayList<>();
        summary = ValidationEngine.builder(Fixtures.catalog())
                .build()
                .validate(Fixtures.records().stream(), outcomes::add);
    }

    @Test
    @DisplayName("every cell of the expected table matches")
    void producesTheExpectedDecisions() {
        assertThat(decisions()).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("r1/countryBlocked", Decision.NOT_APPLICABLE),
                Map.entry("r1/vatFormat", Decision.VALID),
                Map.entry("r1/ibanFormat", Decision.VALID),

                Map.entry("r2/countryBlocked", Decision.NOT_APPLICABLE),
                Map.entry("r2/vatFormat", Decision.INVALID),
                Map.entry("r2/ibanFormat", Decision.VALID),

                Map.entry("r3/countryBlocked", Decision.INVALID),
                Map.entry("r3/vatFormat", Decision.INVALID),
                Map.entry("r3/ibanFormat", Decision.INVALID)));
    }

    @Test
    @DisplayName("one result per rule per record, and no errors")
    void producesOneResultPerRulePerRecord() {
        assertThat(outcomes).hasSize(9).allMatch(RuleResult.class::isInstance);
        assertThat(summary.resultCount()).isEqualTo(9);
        assertThat(summary.errorCount()).isZero();
    }

    /**
     * VALID is r1's vat and iban plus r2's iban; INVALID is r2's vat and all three of r3;
     * NOT_APPLICABLE is countryBlocked on r1 and r2.
     */
    @Test
    @DisplayName("the summary breaks results down by decision and by severity")
    void summarisesTheRun() {
        assertThat(summary.byDecision().get(Decision.VALID)).isEqualTo(3);
        assertThat(summary.byDecision().get(Decision.INVALID)).isEqualTo(4);
        assertThat(summary.byDecision().get(Decision.NOT_APPLICABLE)).isEqualTo(2);
        assertThat(summary.byDecision().get(Decision.REVIEW)).isZero();

        assertThat(summary.bySeverity().get(Severity.ERROR)).isEqualTo(6);
        assertThat(summary.bySeverity().get(Severity.WARNING)).isEqualTo(3);
        assertThat(summary.bySeverity().get(Severity.INFO)).isZero();
    }

    /** INVALID but only a WARNING: the engine never infers one from the other. */
    @Test
    @DisplayName("severity travels with the result, independently of the decision")
    void resultsCarryTheRuleSeverity() {
        RuleResult ibanOnR3 = result("r3", "ibanFormat");

        assertThat(ibanOnR3.decision()).isEqualTo(Decision.INVALID);
        assertThat(ibanOnR3.severity()).isEqualTo(Severity.WARNING);
    }

    /**
     * r3 has no vatId at all, and the read is still recorded with a null value — which is
     * exactly what explains its INVALID.
     */
    @Test
    @DisplayName("provenance names the fields the rule actually read, with their values")
    void resultsCarryProvenance() {
        assertThat(result("r2", "vatFormat").provenance())
                .containsExactly(new FieldRead("vatId", "FR22"));

        assertThat(result("r2", "ibanFormat").provenance())
                .containsExactly(new FieldRead("iban", "FR123456789"), new FieldRead("country", "FR"));

        assertThat(result("r3", "vatFormat").provenance())
                .containsExactly(new FieldRead("vatId", null));
    }

    @Test
    @DisplayName("the computed value is kept, so the mapping's choice is explainable")
    void resultsCarryTheComputedValue() {
        assertThat(result("r3", "countryBlocked").computedValue()).isEqualTo("blocked");
        assertThat(result("r1", "countryBlocked").computedValue()).isEqualTo("ok");
        assertThat(result("r2", "vatFormat").computedValue()).isEqualTo("bad");
    }

    /**
     * The point of the whole design: the same expression with a different table gives a
     * different verdict. In production that is a row update, not a release.
     */
    @Test
    @DisplayName("changing only the mapping changes the decision, with no change to the logic")
    void decisionIsDrivenByDataNotCode() {
        Rule reclassified = Rule.builder("vatFormat")
                .status(RuleStatus.RELEASED)
                .logic(Fixtures.vatFormat().logic())
                .mapping(DecisionMapping.builder()
                        .map("ok", Decision.VALID)
                        .map("bad", Decision.REVIEW)
                        .otherwise(Decision.REVIEW))
                .build();

        List<Outcome> reRun = new ArrayList<>();
        ValidationEngine.builder(List.of(reclassified))
                .build()
                .validate(Fixtures.records().stream(), reRun::add);

        assertThat(reRun).extracting(outcome -> ((RuleResult) outcome).decision())
                .containsExactly(Decision.VALID, Decision.REVIEW, Decision.REVIEW);
    }

    private Map<String, Decision> decisions() {
        return outcomes.stream()
                .map(RuleResult.class::cast)
                .collect(Collectors.toMap(
                        result -> result.recordId() + "/" + result.ruleId(),
                        RuleResult::decision));
    }

    private RuleResult result(String recordId, String ruleId) {
        return outcomes.stream()
                .map(RuleResult.class::cast)
                .filter(r -> r.recordId().equals(recordId) && r.ruleId().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no result for " + recordId + "/" + ruleId));
    }
}
