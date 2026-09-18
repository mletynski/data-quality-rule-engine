package com.dq.example.json;

import com.dq.engine.ValidationEngine;
import com.dq.engine.model.DataRecord;
import com.dq.engine.model.Decision;
import com.dq.engine.model.Outcome;
import com.dq.engine.model.Rule;
import com.dq.engine.model.RuleResult;
import com.dq.engine.model.RuleStatus;
import com.dq.engine.model.RunSummary;
import com.dq.engine.model.Severity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for the host's JSON adapters — the implementation of the {@code DataRecord} and
 * {@code RuleCatalog} seams.
 *
 * <p>Plain unit tests: no Spring context, because nothing here needs one.
 */
class JsonAdapterTest {

    private static InputStream resource(String name) {
        InputStream input = JsonAdapterTest.class.getResourceAsStream(name);
        assertThat(input).as("test resource " + name).isNotNull();
        return input;
    }

    private static InputStream json(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("the JSON path reproduces the task's scenario")
    class EndToEnd {

        @Test
        @DisplayName("records and rules loaded from JSON produce the expected table")
        void jsonDrivenScenario() {
            List<Rule> catalog = JsonRuleCatalog.fromClasspath("/fixture-rules.json");
            List<Outcome> outcomes = new ArrayList<>();

            RunSummary summary;
            try (Stream<DataRecord> records = JsonRecords.fromArray(resource("/fixture-records.json"))) {
                summary = ValidationEngine.builder(catalog).build().validate(records, outcomes::add);
            }

            Map<String, Decision> decisions = outcomes.stream()
                    .map(RuleResult.class::cast)
                    .collect(Collectors.toMap(r -> r.recordId() + "/" + r.ruleId(), RuleResult::decision));

            assertThat(decisions).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                    Map.entry("r1/countryBlocked", Decision.NOT_APPLICABLE),
                    Map.entry("r1/vatFormat", Decision.VALID),
                    Map.entry("r1/ibanFormat", Decision.VALID),
                    Map.entry("r2/countryBlocked", Decision.NOT_APPLICABLE),
                    Map.entry("r2/vatFormat", Decision.INVALID),
                    Map.entry("r2/ibanFormat", Decision.VALID),
                    Map.entry("r3/countryBlocked", Decision.INVALID),
                    Map.entry("r3/vatFormat", Decision.INVALID),
                    Map.entry("r3/ibanFormat", Decision.INVALID)));

            assertThat(summary.errorCount()).isZero();
        }
    }

    @Nested
    @DisplayName("streaming")
    class Streaming {

        @Test
        @DisplayName("JSON is parsed incrementally, so input size does not become heap size")
        void jsonIsStreamedNotMaterialised() {
            int count = 200_000;
            AtomicLong outcomes = new AtomicLong();

            List<Rule> catalog = JsonRuleCatalog.fromClasspath("/fixture-rules.json");

            try (Stream<DataRecord> records = JsonRecords.fromArray(generatedJsonArray(count))) {
                RunSummary summary = ValidationEngine.builder(catalog)
                        .build()
                        .validate(records, outcome -> outcomes.incrementAndGet());

                assertThat(summary.resultCount()).isEqualTo(3L * count);
                assertThat(outcomes.get()).isEqualTo(3L * count);
            }
        }

        /**
         * If the reader materialised the array, obtaining one record would require consuming
         * all of it. Jackson reads ahead by a buffer, but nowhere near the whole document.
         */
        @Test
        @DisplayName("the first record is available before the input has been fully read")
        void firstRecordArrivesEarly() {
            CountingInputStream input = new CountingInputStream(generatedJsonArray(50_000));

            try (Stream<DataRecord> records = JsonRecords.fromArray(input)) {
                DataRecord first = records.findFirst().orElseThrow();

                assertThat(first.id()).isEqualTo("r0");
                assertThat(input.bytesRead()).isLessThan(64 * 1024);
            }
        }

        /**
         * A JSON array produced on the fly. Deliberately not a String or a temp file: if the
         * test had to build the whole document first, it would be measuring the test's
         * memory rather than the adapter's.
         *
         * <p>Index -1 emits the opening bracket and index {@code count} the closing one.
         */
        private static InputStream generatedJsonArray(int count) {
            Enumeration<InputStream> chunks = new Enumeration<>() {
                private int index = -1;

                @Override
                public boolean hasMoreElements() {
                    return index <= count;
                }

                @Override
                public InputStream nextElement() {
                    if (!hasMoreElements()) {
                        throw new NoSuchElementException();
                    }
                    String chunk;
                    if (index == -1) {
                        chunk = "[";
                    } else if (index == count) {
                        chunk = "]";
                    } else {
                        chunk = (index > 0 ? "," : "")
                                + "{\"id\":\"r" + index + "\",\"country\":\"DE\","
                                + "\"vatId\":\"DE12345678" + index + "\",\"iban\":\"DE99" + index + "\"}";
                    }
                    index++;
                    return new ByteArrayInputStream(chunk.getBytes(StandardCharsets.UTF_8));
                }
            };
            return new SequenceInputStream(chunks);
        }
    }

    @Nested
    @DisplayName("catalog loading")
    class CatalogLoading {

        @Test
        @DisplayName("reads metadata, expression and mapping")
        void readsFullRuleDefinition() {
            List<Rule> catalog = JsonRuleCatalog.fromClasspath("/fixture-rules.json");

            assertThat(catalog).hasSize(3);
            assertThat(catalog.getFirst()).satisfies(rule -> {
                assertThat(rule.id()).isEqualTo("countryBlocked");
                assertThat(rule.status()).isEqualTo(RuleStatus.RELEASED);
                assertThat(rule.severity()).isEqualTo(Severity.ERROR);
                assertThat(rule.country()).isNull();
                assertThat(rule.categories()).containsExactly("compliance");
                assertThat(rule.mapping().decide("blocked")).isEqualTo(Decision.INVALID);
                assertThat(rule.mapping().decide("anything else")).isEqualTo(Decision.NOT_APPLICABLE);
            });
        }

        @Test
        @DisplayName("a country-scoped rule is read as such")
        void readsCountryScope() {
            List<Rule> catalog = JsonRuleCatalog.load(json("""
                    [{"id":"de1","status":"RELEASED","severity":"INFO","country":"DE",
                      "value":"'ok'","mapping":{"cases":{"ok":"VALID"},"default":"REVIEW"}}]
                    """));

            assertThat(catalog.getFirst().country()).isEqualTo("DE");
        }

        @Test
        @DisplayName("an omitted or WORLD country means the rule applies everywhere")
        void worldIsTheDefault() {
            List<Rule> catalog = JsonRuleCatalog.load(json("""
                    [{"id":"a","status":"RELEASED","severity":"INFO",
                      "value":"'ok'","mapping":{"default":"REVIEW"}},
                     {"id":"b","status":"RELEASED","severity":"INFO","country":"WORLD",
                      "value":"'ok'","mapping":{"default":"REVIEW"}}]
                    """));

            assertThat(catalog).allSatisfy(rule -> assertThat(rule.country()).isNull());
        }

        @Test
        @DisplayName("a broken expression fails at load time, naming the rule")
        void malformedExpressionFailsAtLoad() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> JsonRuleCatalog.load(json("""
                            [{"id":"broken","status":"RELEASED","severity":"ERROR",
                              "value":"country == ","mapping":{"default":"REVIEW"}}]
                            """)))
                    .withMessageContaining("invalid rule 'broken'");
        }

        @Test
        @DisplayName("a mapping without a default is rejected, because it would not be total")
        void mappingWithoutDefaultRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> JsonRuleCatalog.load(json("""
                            [{"id":"noDefault","status":"RELEASED","severity":"ERROR",
                              "value":"'ok'","mapping":{"cases":{"ok":"VALID"}}}]
                            """)))
                    .withMessageContaining("no 'default' decision");
        }

        @Test
        @DisplayName("an unknown enum constant is rejected with the valid options")
        void unknownEnumRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> JsonRuleCatalog.load(json("""
                            [{"id":"wrongSeverity","status":"RELEASED","severity":"CRITICAL",
                              "value":"'ok'","mapping":{"default":"REVIEW"}}]
                            """)))
                    .withMessageContaining("not a valid Severity");
        }
    }

    @Nested
    @DisplayName("record reading")
    class RecordReading {

        @Test
        @DisplayName("a JSON null is treated as an absent field")
        void jsonNullIsAbsent() {
            try (Stream<DataRecord> records = JsonRecords.fromArray(
                    json("[{\"id\":\"x\",\"vatId\":null}]"))) {
                assertThat(records.findFirst().orElseThrow().get("vatId")).isEmpty();
            }
        }

        @Test
        @DisplayName("a record with no id gets a positional one, so its outcomes stay attributable")
        void missingIdIsSynthesised() {
            try (Stream<DataRecord> records = JsonRecords.fromArray(
                    json("[{\"country\":\"DE\"},{\"id\":\"\",\"country\":\"FR\"}]"))) {
                assertThat(records.map(DataRecord::id)).containsExactly("#0", "#1");
            }
        }

        @Test
        @DisplayName("a non-array top level is rejected immediately")
        void nonArrayRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> JsonRecords.fromArray(json("{\"id\":\"x\"}")))
                    .withMessageContaining("expected a JSON array");
        }

        @Test
        @DisplayName("malformed JSON part-way through is reported, not silently truncated")
        void malformedJsonIsReported() {
            try (Stream<DataRecord> records = JsonRecords.fromArray(
                    json("[{\"id\":\"a\"},{\"id\":\"b\",}]"))) {
                assertThatExceptionOfType(RuntimeException.class).isThrownBy(records::toList);
            }
        }

    }

    /** Counts bytes actually pulled from the underlying stream. */
    private static final class CountingInputStream extends InputStream {

        private final InputStream delegate;
        private long bytesRead;

        private CountingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        long bytesRead() {
            return bytesRead;
        }

        @Override
        public int read() throws java.io.IOException {
            int value = delegate.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }

        @Override
        public void close() throws java.io.IOException {
            delegate.close();
        }
    }
}
