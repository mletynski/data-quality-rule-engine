package com.dq.example.json;

import com.dq.engine.model.Decision;
import com.dq.engine.model.DecisionMapping;
import com.dq.engine.model.DecisionMappingBuilder;
import com.dq.engine.model.Rule;
import com.dq.engine.model.RuleStatus;
import com.dq.engine.model.Severity;
import com.dq.engine.spel.SpelRuleLogic;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loads rules from JSON:
 *
 * <pre>{@code
 * {
 *   "id": "vatFormat",
 *   "status": "RELEASED",
 *   "severity": "ERROR",
 *   "country": "WORLD",
 *   "categories": ["tax"],
 *   "value": "vatId != null and vatId.length() >= 9 ? 'ok' : 'bad'",
 *   "mapping": { "cases": { "ok": "VALID", "bad": "INVALID" }, "default": "REVIEW" }
 * }
 * }</pre>
 *
 * <p>A host concern, like {@link JsonRecords}: where rules are stored is the host's
 * decision. This application uses a classpath file; production would read MariaDB behind
 * a Redis-backed cache. The engine just gets a {@code List<Rule>} either way.
 *
 * <p>Everything is validated here, at load time — expressions parsed, enums resolved,
 * mappings checked for a default — so a broken rule fails once at startup, naming the
 * rule, instead of a million times mid-run.
 */
public final class JsonRuleCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonRuleCatalog() {
    }

    /**
     * Reads the whole catalog eagerly — unlike records, it is small and bounded, so
     * streaming would buy nothing and cost clarity.
     *
     * @throws IllegalArgumentException if any rule is invalid, naming the rule and the problem
     */
    public static List<Rule> load(InputStream input) {
        Objects.requireNonNull(input, "input");
        List<RuleDefinition> definitions = MAPPER.readValue(input, new TypeReference<>() {
        });
        return definitions.stream().map(JsonRuleCatalog::toRule).toList();
    }

    public static List<Rule> fromClasspath(String resource) {
        try (InputStream input = JsonRuleCatalog.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("rule catalog not found on classpath: " + resource);
            }
            return load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read rule catalog " + resource, e);
        }
    }

    /**
     * Any failure is rethrown naming the rule. "EL1041E: after parsing a valid expression,
     * there is still more data" is useless without knowing which of forty rules produced
     * it.
     */
    private static Rule toRule(RuleDefinition definition) {
        String id = require(definition.id(), "id", "<missing id>");
        try {
            return Rule.builder(id)
                    .label(definition.label() == null ? id : definition.label())
                    .status(parseEnum(RuleStatus.class, require(definition.status(), "status", id)))
                    .severity(parseEnum(Severity.class, require(definition.severity(), "severity", id)))
                    .country(parseCountry(definition.country()))
                    .categories(definition.categories() == null
                            ? new String[0]
                            : definition.categories().toArray(String[]::new))
                    .logic(SpelRuleLogic.of(require(definition.value(), "value", id)))
                    .mapping(toMapping(definition.mapping(), id))
                    .build();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid rule '" + id + "': " + e.getMessage(), e);
        }
    }

    /**
     * The {@code default} decision is mandatory: it is what makes the mapping total, so
     * that any value a rule computes — including one nobody anticipated — yields a decision
     * rather than an error.
     */
    private static DecisionMapping toMapping(MappingDefinition mapping, String ruleId) {
        if (mapping == null) {
            throw new IllegalArgumentException("rule '" + ruleId + "' has no decision mapping");
        }
        if (mapping.defaultDecision() == null) {
            throw new IllegalArgumentException(
                    "rule '" + ruleId + "' has no 'default' decision; every mapping needs a fallback");
        }

        DecisionMappingBuilder builder = DecisionMapping.builder();
        Map<String, String> cases = mapping.cases() == null ? Map.of() : mapping.cases();
        cases.forEach((value, decision) -> builder.map(value, parseEnum(Decision.class, decision)));
        return builder.otherwise(parseEnum(Decision.class, mapping.defaultDecision()));
    }

    /** {@code null}, blank or "WORLD" all mean "applies everywhere". */
    private static String parseCountry(String country) {
        return country == null || country.isBlank() || "WORLD".equalsIgnoreCase(country) ? null : country;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + value + "' is not a valid " + type.getSimpleName()
                    + "; expected one of " + Set.of(type.getEnumConstants()));
        }
    }

    private static String require(String value, String field, String ruleId) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("rule '" + ruleId + "' is missing required field '" + field + "'");
        }
        return value;
    }

}
