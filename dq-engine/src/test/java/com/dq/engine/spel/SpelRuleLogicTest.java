package com.dq.engine.spel;

import com.dq.engine.model.DataRecord;
import com.dq.engine.model.Evaluation;
import com.dq.engine.model.FieldRead;
import com.dq.engine.model.MapDataRecord;
import com.dq.engine.model.RuleEvaluationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.expression.ParseException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SpelRuleLogicTest {

    private static final DataRecord RECORD = new MapDataRecord("r1", Map.of(
            "vatId", "DE111111111",
            "country", "DE",
            "iban", "DE123456789"));

    @Nested
    @DisplayName("evaluation")
    class Evaluating {

        @Test
        @DisplayName("computes a value from the record's fields")
        void computesValue() {
            Evaluation evaluation =
                    SpelRuleLogic.of("country == 'DE' ? 'ok' : 'bad'").evaluate(RECORD);

            assertThat(evaluation.value()).isEqualTo("ok");
        }

        /** What {@code withInstanceMethods()} buys: rules read naturally rather than being
         * restricted to operators. */
        @Test
        @DisplayName("instance methods on field values are available")
        void allowsInstanceMethods() {
            assertThat(SpelRuleLogic.of("iban.startsWith(country) ? 'ok' : 'bad'").evaluate(RECORD).value())
                    .isEqualTo("ok");
            assertThat(SpelRuleLogic.of("vatId.length() >= 9 ? 'ok' : 'bad'").evaluate(RECORD).value())
                    .isEqualTo("ok");
        }

        @Test
        @DisplayName("an absent field reads as null rather than failing")
        void absentFieldIsNull() {
            Evaluation evaluation =
                    SpelRuleLogic.of("nope == null ? 'missing' : 'present'").evaluate(RECORD);

            assertThat(evaluation.value()).isEqualTo("missing");
            assertThat(evaluation.provenance()).containsExactly(new FieldRead("nope", null));
        }

        /** {@code vatId} is referenced twice in the expression but appears once. */
        @Test
        @DisplayName("provenance records each field once, at first read, in read order")
        void capturesProvenance() {
            Evaluation evaluation = SpelRuleLogic
                    .of("vatId != null and vatId.length() >= 9 and country != null ? 'ok' : 'bad'")
                    .evaluate(RECORD);

            assertThat(evaluation.provenance()).containsExactly(
                    new FieldRead("vatId", "DE111111111"),
                    new FieldRead("country", "DE"));
        }

        /** {@code iban} is never reached, because the first operand is false. */
        @Test
        @DisplayName("short-circuiting means unread fields are not claimed as provenance")
        void provenanceReflectsWhatWasActuallyRead() {
            Evaluation evaluation = SpelRuleLogic
                    .of("country == 'FR' and iban != null ? 'ok' : 'bad'")
                    .evaluate(RECORD);

            assertThat(evaluation.provenance()).containsExactly(new FieldRead("country", "DE"));
        }
    }

    @Nested
    @DisplayName("failure handling")
    class Failures {

        /**
         * The catalog is loaded once at startup, so a typo must surface there rather than a
         * million times in the middle of a run.
         */
        @Test
        @DisplayName("a malformed expression fails at construction, not at evaluation")
        void malformedExpressionFailsEarly() {
            assertThatExceptionOfType(ParseException.class)
                    .isThrownBy(() -> SpelRuleLogic.of("country == "));
        }

        @Test
        @DisplayName("an evaluation failure keeps the provenance gathered so far")
        void failureKeepsProvenance() {
            assertThatExceptionOfType(RuleEvaluationException.class)
                    .isThrownBy(() -> SpelRuleLogic.of("country.thisMethodDoesNotExist()").evaluate(RECORD))
                    .satisfies(failure -> assertThat(failure.provenance())
                            .containsExactly(new FieldRead("country", "DE")));
        }

        @Test
        @DisplayName("a non-String result is rejected rather than coerced")
        void nonStringResultRejected() {
            assertThatExceptionOfType(RuleEvaluationException.class)
                    .isThrownBy(() -> SpelRuleLogic.of("vatId.length()").evaluate(RECORD))
                    .withMessageContaining("rule expression failed");
        }
    }

    /**
     * Rules are data. Data gets edited, imported, and eventually supplied by someone you do
     * not control, so every expression below must stay impossible. Each is a real SpEL
     * injection technique.
     *
     * <p>Some are rejected while parsing and others while evaluating. Either way the rule
     * never runs, which is the property that matters.
     */
    @Nested
    @DisplayName("sandbox")
    class Sandbox {

        /** Type references are the gateway to the entire JDK, and the shape of most real-world
         * SpEL remote-code-execution exploits. */
        @ParameterizedTest(name = "refuses: {0}")
        @ValueSource(strings = {
                "T(java.lang.Runtime).getRuntime().exec('echo pwned')",
                "T(java.lang.System).getProperty('user.home')",
                "T(java.lang.Class).forName('java.lang.Runtime')",
        })
        @DisplayName("refuses type references")
        void refusesTypeReferences(String expression) {
            assertRefused(expression);
        }

        @Test
        @DisplayName("refuses constructors")
        void refusesConstructors() {
            assertRefused("new java.lang.ProcessBuilder('echo').start()");
        }

        @Test
        @DisplayName("refuses bean references into a surrounding Spring context")
        void refusesBeanReferences() {
            assertRefused("@someBean.doSomething()");
        }

        /**
         * The reflection pivot, and the single most important line of defence:
         * {@code getClass()} is declared on {@code java.lang.Object}, and
         * {@code DataBindingMethodResolver} refuses methods declared there. Without that one
         * restriction every other defence in this class is bypassable.
         */
        @ParameterizedTest(name = "refuses: {0}")
        @ValueSource(strings = {
                "country.getClass().getName()",
                "country.getClass().getClassLoader()",
        })
        @DisplayName("refuses the reflection pivot through getClass()")
        void refusesTheReflectionPivot(String expression) {
            assertRefused(expression);
        }

        @Test
        @DisplayName("refuses to modify the record")
        void cannotWriteToTheRecord() {
            assertRefused("country = 'XX'");
        }

        private void assertRefused(String expression) {
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> SpelRuleLogic.of(expression).evaluate(RECORD));
        }
    }
}
