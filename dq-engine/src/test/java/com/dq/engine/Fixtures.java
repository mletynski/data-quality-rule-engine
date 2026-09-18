package com.dq.engine;

import com.dq.engine.model.DataRecord;
import com.dq.engine.model.Decision;
import com.dq.engine.model.DecisionMapping;
import com.dq.engine.model.MapDataRecord;
import com.dq.engine.model.Rule;
import com.dq.engine.model.RuleStatus;
import com.dq.engine.model.Severity;
import com.dq.engine.spel.SpelRuleLogic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The records and rules from section 5 of the task.
 *
 * <p>Note there is no JSON here. These are {@link MapDataRecord}s, so the scenario is
 * exercised without a JSON library — the evidence that {@link DataRecord} is a real seam.
 */
final class Fixtures {

    private Fixtures() {
    }

    static List<DataRecord> records() {
        return List.of(r1(), r2(), r3());
    }

    /** Complete German record: nothing wrong with it. */
    static DataRecord r1() {
        return new MapDataRecord("r1", fields(
                "vatId", "DE111111111",
                "country", "DE",
                "legalName", "ACME GmbH",
                "iban", "DE123456789"));
    }

    /** French record with a four-character VAT id. */
    static DataRecord r2() {
        return new MapDataRecord("r2", fields(
                "vatId", "FR22",
                "country", "FR",
                "legalName", "Bricolage SARL",
                "iban", "FR123456789"));
    }

    /** Blocked country, no VAT id at all, empty IBAN. */
    static DataRecord r3() {
        return new MapDataRecord("r3", fields(
                "country", "ZZ",
                "legalName", "Nowhere Ltd",
                "iban", ""));
    }

    /** Each rule is an expression producing a value, plus a table turning it into a decision. */
    static List<Rule> catalog() {
        return List.of(countryBlocked(), vatFormat(), ibanFormat());
    }

    static Rule countryBlocked() {
        return Rule.builder("countryBlocked")
                .label("Country is on the blocked list")
                .status(RuleStatus.RELEASED)
                .severity(Severity.ERROR)
                .categories("compliance")
                .logic(SpelRuleLogic.of("country == 'ZZ' ? 'blocked' : 'ok'"))
                .mapping(DecisionMapping.builder()
                        .map("blocked", Decision.INVALID)
                        .otherwise(Decision.NOT_APPLICABLE))
                .build();
    }

    static Rule vatFormat() {
        return Rule.builder("vatFormat")
                .label("VAT id is present and long enough")
                .status(RuleStatus.RELEASED)
                .severity(Severity.ERROR)
                .categories("tax")
                .logic(SpelRuleLogic.of("vatId != null and vatId.length() >= 9 ? 'ok' : 'bad'"))
                .mapping(DecisionMapping.builder()
                        .map("ok", Decision.VALID)
                        .map("bad", Decision.INVALID)
                        .otherwise(Decision.REVIEW))
                .build();
    }

    static Rule ibanFormat() {
        return Rule.builder("ibanFormat")
                .label("IBAN starts with the record's country")
                .status(RuleStatus.RELEASED)
                .severity(Severity.WARNING)
                .categories("bank")
                .logic(SpelRuleLogic.of(
                        "iban != null and country != null and iban.startsWith(country) ? 'ok' : 'bad'"))
                .mapping(DecisionMapping.builder()
                        .map("ok", Decision.VALID)
                        .map("bad", Decision.INVALID)
                        .otherwise(Decision.REVIEW))
                .build();
    }

    private static Map<String, Object> fields(String... keysAndValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            fields.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return fields;
    }
}
