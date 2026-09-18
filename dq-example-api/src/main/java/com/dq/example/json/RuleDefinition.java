package com.dq.example.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Wire format of one rule. A dumb DTO of strings on purpose: validation happens in
 * {@link JsonRuleCatalog}, so a bad enum names the rule rather than a byte offset.
 *
 * @param country an ISO code, or {@code "WORLD"}/blank/absent for every country
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RuleDefinition(
        String id,
        String label,
        String status,
        String severity,
        String country,
        List<String> categories,
        String value,
        MappingDefinition mapping) {
}
