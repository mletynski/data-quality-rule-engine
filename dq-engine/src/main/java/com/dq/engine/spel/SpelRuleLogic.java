package com.dq.engine.spel;

import com.dq.engine.model.DataRecord;
import com.dq.engine.model.Evaluation;
import com.dq.engine.model.RuleEvaluationException;
import com.dq.engine.model.RuleLogic;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Objects;

/**
 * {@link RuleLogic} backed by a stored Spring Expression Language expression, e.g.
 * {@code "vatId != null and vatId.length() >= 9 ? 'ok' : 'bad'"}. This is what makes the
 * catalog data: a rule is a row that can be edited without a build.
 *
 * <p>Rules are data and data gets edited, so a rule is an injection vector.
 * {@link SimpleEvaluationContext} with a single property accessor is what closes that:
 * no type references, no constructors, no bean references, and no route from an expression
 * to anything but the record's own fields. See DESIGN.md for the full argument and
 * {@code SpelRuleLogicTest.Sandbox} for the escape routes it blocks.
 *
 * <p>Expressions are parsed in the constructor, so a typo fails once at catalog load rather
 * than a million times mid-run. Compilation is off because a custom property accessor is
 * not compilable, which is the price of exact provenance.
 *
 * <p>Immutable and thread-safe; one instance serves every record in a run.
 */
public final class SpelRuleLogic implements RuleLogic {

    private static final ExpressionParser PARSER =
            new SpelExpressionParser(new SpelParserConfiguration(SpelCompilerMode.OFF, null));

    /** Stateless and shared: the root object is supplied per evaluation. */
    private static final EvaluationContext CONTEXT = SimpleEvaluationContext
            .forPropertyAccessors(new RecordingPropertyAccessor())
            .withInstanceMethods()
            .build();

    private final String source;
    private final Expression expression;

    private SpelRuleLogic(String source) {
        this.source = source;
        this.expression = PARSER.parseExpression(source);
    }

    /** @throws org.springframework.expression.ParseException if the expression is malformed */
    public static SpelRuleLogic of(String source) {
        Objects.requireNonNull(source, "source");
        if (source.isBlank()) {
            throw new IllegalArgumentException("rule expression must not be blank");
        }
        return new SpelRuleLogic(source);
    }

    /**
     * A non-{@code String} result is rejected rather than coerced: a rule returning a
     * boolean is an authoring mistake, and should surface as a located failure rather than
     * a decision that happens to hit the mapping's default.
     *
     * @throws RuleEvaluationException wrapping any failure, carrying the fields read before
     *                                 it so the error can say what the rule saw
     */
    @Override
    public Evaluation evaluate(DataRecord record) {
        RecordView view = new RecordView(record);
        try {
            Object raw = expression.getValue(CONTEXT, view);
            if (raw instanceof String value) {
                return new Evaluation(value, view.reads());
            }
            throw new IllegalStateException("expression must produce a String value but produced "
                    + (raw == null ? "null" : raw.getClass().getSimpleName() + " (" + raw + ")"));

        } catch (RuntimeException failure) {
            throw new RuleEvaluationException("rule expression failed: " + source, failure, view.reads());
        }
    }
}
