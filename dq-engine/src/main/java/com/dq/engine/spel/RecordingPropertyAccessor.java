package com.dq.engine.spel;

import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;

/**
 * Resolves a bare name in an expression ({@code vatId}, {@code iban}) against the
 * {@link RecordView}, journalling the read.
 *
 * <p>Without it SpEL would fall back to reflective JavaBean access and look for
 * {@code getVatId()}, which no record has. Being the only registered accessor is also the
 * security property: it is the sole route from an expression to any data.
 */
final class RecordingPropertyAccessor implements PropertyAccessor {

    @Override
    public Class<?>[] getSpecificTargetClasses() {
        return new Class<?>[]{RecordView.class};
    }

    /**
     * Every name is readable, and an absent field reads as null. Refusing unknown names
     * would turn ordinary sparse data into a rule failure, when what a rule wants is to
     * write {@code vatId != null}.
     */
    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) {
        return target instanceof RecordView;
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name)
            throws AccessException {
        if (!(target instanceof RecordView view)) {
            throw new AccessException("not a record view: " + target);
        }
        Object value = view.read(name);
        return value == null ? TypedValue.NULL : new TypedValue(value);
    }

    /** A rule inspects records; it never modifies them. */
    @Override
    public boolean canWrite(EvaluationContext context, Object target, String name) {
        return false;
    }

    @Override
    public void write(EvaluationContext context, Object target, String name, Object newValue)
            throws AccessException {
        throw new AccessException("rules must not modify records (attempted to write '" + name + "')");
    }
}
