package com.dq.example;

/** One provenance entry on the wire: the field a rule read, and the value it saw. */
public record FieldView(String field, Object value) {
}
