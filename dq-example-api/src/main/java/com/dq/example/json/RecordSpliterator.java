package com.dq.example.json;

import com.dq.engine.model.DataRecord;
import com.dq.engine.model.MapDataRecord;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectReader;

import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * Pulls one record per {@code tryAdvance} from a parser already positioned inside a JSON
 * array. Sized as unknown, because the whole point is that the records are never counted up
 * front.
 */
final class RecordSpliterator extends Spliterators.AbstractSpliterator<DataRecord> {

    private static final String ID_FIELD = "id";

    private final JsonParser parser;
    private final ObjectReader recordReader;
    private long index;

    RecordSpliterator(JsonParser parser, ObjectReader recordReader) {
        super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE);
        this.parser = parser;
        this.recordReader = recordReader;
    }

    @Override
    public boolean tryAdvance(Consumer<? super DataRecord> action) {
        JsonToken token = parser.nextToken();
        if (token == null || token == JsonToken.END_ARRAY) {
            return false;
        }
        if (token != JsonToken.START_OBJECT) {
            throw new IllegalArgumentException(
                    "expected a JSON object at record index " + index + " but found " + token);
        }

        action.accept(toRecord(recordReader.readValue(parser)));
        index++;
        return true;
    }

    /**
     * JSON nulls are dropped rather than stored: an explicit null and an absent field mean
     * the same thing to a rule, and collapsing them here keeps that decision in one place
     * instead of in every rule expression.
     *
     * <p>A record with no usable id is given a positional one. Such a record still has to be
     * validated — it is exactly the kind a data-quality run exists to find — and a
     * synthesised id keeps its outcomes attributable.
     */
    private DataRecord toRecord(Map<String, Object> fields) {
        fields.values().removeIf(Objects::isNull);

        Object rawId = fields.get(ID_FIELD);
        String recordId = rawId == null || rawId.toString().isBlank()
                ? "#" + index
                : rawId.toString();

        return new MapDataRecord(recordId, fields);
    }
}
