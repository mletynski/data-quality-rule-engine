package com.dq.engine.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link DataRecord} backed by a plain {@code Map}.
 *
 * @param fields copied on construction, which also rejects null values: an absent field is
 *               expressed by leaving the key out, not by mapping it to null
 */
public record MapDataRecord(String id, Map<String, Object> fields) implements DataRecord {

    public MapDataRecord {
        Objects.requireNonNull(id, "id");
        fields = Map.copyOf(fields);
    }

    @Override
    public Optional<Object> get(String field) {
        return Optional.ofNullable(fields.get(field));
    }

    @Override
    public Optional<String> country() {
        return get("country").map(Object::toString);
    }
}
