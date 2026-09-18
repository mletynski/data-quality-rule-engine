package com.dq.engine;

import com.dq.engine.model.DataRecord;
import com.dq.engine.model.Decision;
import com.dq.engine.model.DecisionMapping;
import com.dq.engine.model.MapDataRecord;
import com.dq.engine.model.Outcome;
import com.dq.engine.model.Rule;
import com.dq.engine.model.RuleStatus;
import com.dq.engine.model.Severity;
import com.dq.engine.spel.SpelRuleLogic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Rule selection, and the invariants the engine enforces whatever the caller selects. */
class RuleSelectionTest {

    private static Rule rule(String id, RuleStatus status, String country, String... categories) {
        return Rule.builder(id)
                .status(status)
                .severity(Severity.INFO)
                .country(country)
                .categories(categories)
                .logic(SpelRuleLogic.of("'ok'"))
                .mapping(DecisionMapping.builder().map("ok", Decision.VALID).otherwise(Decision.REVIEW))
                .build();
    }

    /** The rules that actually ran, as {@code recordId/ruleId}. */
    private static List<String> ran(List<Rule> catalog, Predicate<Rule> selection, DataRecord... records) {
        List<Outcome> outcomes = new ArrayList<>();
        ValidationEngine.builder(catalog)
                .select(selection)
                .build()
                .validate(Stream.of(records), outcomes::add);
        return outcomes.stream().map(o -> o.recordId() + "/" + o.ruleId()).toList();
    }

    private static List<String> ran(List<Rule> catalog, DataRecord... records) {
        return ran(catalog, rule -> true, records);
    }

    @Nested
    @DisplayName("the RELEASED invariant")
    class ReleasedOnly {

        private final List<Rule> mixed = List.of(
                rule("draft", RuleStatus.DRAFT, null),
                rule("released", RuleStatus.RELEASED, null),
                rule("deprecated", RuleStatus.DEPRECATED, null));

        @Test
        @DisplayName("no selection runs RELEASED rules only")
        void defaultSelectionIsReleasedOnly() {
            assertThat(ran(mixed, Fixtures.r1())).containsExactly("r1/released");
        }

        /**
         * The invariant is intersected with the caller's selection, so it cannot be
         * defeated — not by asking for DRAFT directly, and not by negating a RELEASED
         * check.
         */
        @Test
        @DisplayName("explicitly asking for DRAFT rules runs nothing")
        void askingForDraftRunsNothing() {
            assertThat(ran(mixed, rule -> rule.status() == RuleStatus.DRAFT, Fixtures.r1())).isEmpty();
            assertThat(ran(mixed, Predicate.not(Rule::isReleased), Fixtures.r1())).isEmpty();
        }
    }

    @Nested
    @DisplayName("selection")
    class Selection {

        private final List<Rule> catalog = List.of(
                rule("tax1", RuleStatus.RELEASED, null, "tax"),
                rule("bank1", RuleStatus.RELEASED, null, "bank"),
                rule("taxBank", RuleStatus.RELEASED, null, "tax", "bank"),
                rule("untagged", RuleStatus.RELEASED, null));

        @Test
        @DisplayName("filter by category")
        void byCategory() {
            assertThat(ran(catalog, rule -> rule.categories().contains("tax"), Fixtures.r1()))
                    .containsExactly("r1/tax1", "r1/taxBank");
        }

        @Test
        @DisplayName("selections compose with the JDK's own and / or / negate")
        void selectionsCompose() {
            Predicate<Rule> tax = rule -> rule.categories().contains("tax");
            Predicate<Rule> bank = rule -> rule.categories().contains("bank");

            assertThat(ran(catalog, tax.and(bank), Fixtures.r1())).containsExactly("r1/taxBank");
            assertThat(ran(catalog, tax.or(bank), Fixtures.r1()))
                    .containsExactly("r1/tax1", "r1/bank1", "r1/taxBank");
            assertThat(ran(catalog, tax.negate(), Fixtures.r1()))
                    .containsExactly("r1/bank1", "r1/untagged");
        }
    }

    @Nested
    @DisplayName("country scope")
    class CountryScope {

        private final List<Rule> catalog = List.of(
                rule("worldwide", RuleStatus.RELEASED, null),
                rule("germanOnly", RuleStatus.RELEASED, "DE"),
                rule("frenchOnly", RuleStatus.RELEASED, "FR"));

        /** r1 is a German record and r2 a French one. */
        @Test
        @DisplayName("a country-scoped rule runs only on records of that country")
        void scopedRulesMatchTheRecordCountry() {
            assertThat(ran(catalog, Fixtures.r1(), Fixtures.r2())).containsExactly(
                    "r1/worldwide", "r1/germanOnly",
                    "r2/worldwide", "r2/frenchOnly");
        }

        /** r3's country is ZZ, which no rule in this catalog names. */
        @Test
        @DisplayName("a record whose country matches no rule still gets the world rules")
        void unknownCountryStillGetsWorldRules() {
            assertThat(ran(catalog, Fixtures.r3())).containsExactly("r3/worldwide");
        }

        /** Records with missing data are the ones a quality run most needs to look at. */
        @Test
        @DisplayName("a record with no country at all is still checked by world rules")
        void missingCountryStillGetsWorldRules() {
            DataRecord noCountry = new MapDataRecord("nc", Map.of("legalName", "Anon Ltd"));

            assertThat(ran(catalog, noCountry)).containsExactly("nc/worldwide");
        }
    }
}
