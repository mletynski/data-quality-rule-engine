package com.dq.engine.model;

import java.util.Optional;

/**
 * One business-partner record, as the engine sees it.
 *
 * <p>The seam for host-supplied input: the engine has no JSON library and no opinion about
 * where records come from. A host adapts JSON, Avro, a {@code ResultSet} row or a
 * {@code Map} into this. {@link MapDataRecord} is the shipped implementation.
 *
 * <p>Implementations must be effectively immutable and safe for concurrent reads.
 */
public interface DataRecord {

    String id();

    /** @return the value, or empty if the field is absent or null */
    Optional<Object> get(String field);

    /**
     * The record's country, used to decide whether a country-scoped rule applies. Empty if
     * the record has none — such a record is still checked by every rule that is not
     * country-scoped.
     */
    Optional<String> country();
}
