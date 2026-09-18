package com.dq.engine.spel;

import com.dq.engine.model.DataRecord;
import com.dq.engine.model.FieldRead;

import java.util.ArrayList;
import java.util.List;

/**
 * The object a SpEL expression evaluates against: a record, plus a journal of the fields it
 * touched.
 *
 * <p>This is how provenance is captured rather than declared: a rule author writes
 * {@code vatId != null} and never thinks about it, so a rule cannot under-report the fields
 * it used or drift out of sync with its own expression.
 *
 * <p>One instance per evaluation, confined to that evaluation's thread.
 */
final class RecordView {

    private final DataRecord record;
    private final List<FieldRead> reads = new ArrayList<>(4);

    RecordView(DataRecord record) {
        this.record = record;
    }

    /**
     * Journals a field only on first read. Expressions touch a field more than once
     * ({@code vatId != null and vatId.length() >= 9} reads it twice) and the record cannot
     * change mid-evaluation, so repeats would be noise.
     */
    Object read(String field) {
        Object value = record.get(field).orElse(null);

        for (FieldRead seen : reads) {
            if (seen.field().equals(field)) {
                return value;
            }
        }
        reads.add(new FieldRead(field, value));
        return value;
    }

    /** The reads so far, in order. Safe to call after a failure, which is the point. */
    List<FieldRead> reads() {
        return List.copyOf(reads);
    }
}
