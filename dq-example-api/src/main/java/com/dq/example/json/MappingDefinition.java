package com.dq.example.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Wire format of a rule's decision mapping: the case table plus its mandatory fallback. */
@JsonIgnoreProperties(ignoreUnknown = true)
record MappingDefinition(
        Map<String, String> cases,
        @JsonProperty("default") String defaultDecision) {
}
