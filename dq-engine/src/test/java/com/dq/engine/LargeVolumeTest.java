package com.dq.engine;

import com.dq.engine.model.DataRecord;
import com.dq.engine.model.MapDataRecord;
import com.dq.engine.model.Outcome;
import com.dq.engine.model.RunSummary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * About a million records per run, with memory that does not grow with the batch.
 *
 * <p>The test JVM is capped at 256 MB (see {@code build.gradle.kts}). That cap is the
 * assertion: an engine that accumulated its results could not finish these tests.
 *
 * <p>No JSON here — the engine has no JSON library. The streaming-input half is tested in
 * {@code dq-example-api}, where the adapter lives.
 */
class LargeVolumeTest {

    private static final int MILLION = 1_000_000;

    /** Generated on demand — never a list, so the source itself is bounded too. */
    private static Stream<DataRecord> generatedRecords(int count) {
        return Stream.iterate(0, index -> index + 1)
                .limit(count)
                .map(index -> {
                    Map<String, Object> fields = new LinkedHashMap<>();
                    fields.put("vatId", index % 3 == 0 ? "DE12345678" + index : "X");
                    fields.put("country", index % 2 == 0 ? "DE" : "FR");
                    fields.put("iban", index % 2 == 0 ? "DE99" + index : "ZZ99");
                    return (DataRecord) new MapDataRecord("r" + index, fields);
                });
    }

    /**
     * The final assertion also shows the summary is counters only: it costs the same at a
     * million records as at three, which is why it is the one thing {@code validate}
     * returns by value.
     */
    @Test
    @DisplayName("a million records complete within a 256 MB heap")
    void millionRecordsInBoundedMemory() {
        AtomicLong seen = new AtomicLong();

        RunSummary summary = ValidationEngine.builder(Fixtures.catalog())
                .build()
                .validate(generatedRecords(MILLION), outcome -> seen.incrementAndGet());

        assertThat(summary.resultCount()).isEqualTo(3L * MILLION);
        assertThat(summary.errorCount()).isZero();
        assertThat(seen.get()).isEqualTo(3L * MILLION);

        assertThat(summary.byDecision().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(3L * MILLION);
    }

    /**
     * The direct proof of streaming: how many records has the source produced when the very
     * first outcome arrives? If the engine buffered, it would be all of them.
     */
    @Test
    @DisplayName("outcomes are emitted while the input is still being read, not after")
    void emitsBeforeTheInputIsExhausted() {
        AtomicLong produced = new AtomicLong();
        AtomicLong producedAtFirstOutcome = new AtomicLong(-1);

        Stream<DataRecord> records = generatedRecords(10_000).peek(record -> produced.incrementAndGet());

        ValidationEngine.builder(Fixtures.catalog())
                .build()
                .validate(records, outcome -> producedAtFirstOutcome.compareAndSet(-1, produced.get()));

        assertThat(producedAtFirstOutcome.get()).isEqualTo(1);
    }

    /**
     * Five records at a batch size of two: flushes after records 2 and 4, plus the final
     * flush that guarantees nothing is left buffered when {@code validate} returns.
     */
    @Test
    @DisplayName("the batch size controls how often the handler is flushed")
    void batchSizeDrivesFlushes() {
        List<String> events = new ArrayList<>();

        OutcomeHandler outcomes = new OutcomeHandler() {
            @Override
            public void handle(Outcome outcome) {
            }

            @Override
            public void flush() {
                events.add("flush");
            }
        };

        ValidationEngine.builder(Fixtures.catalog())
                .batchSize(2)
                .build()
                .validate(generatedRecords(5), outcomes);

        assertThat(events).hasSize(3);
    }

    /** For an I/O-backed source this is what releases the file handle or the connection. */
    @Test
    @DisplayName("the engine closes the record stream, releasing whatever backs it")
    void closesTheRecordStream() {
        AtomicLong closed = new AtomicLong();
        Stream<DataRecord> records = generatedRecords(10).onClose(closed::incrementAndGet);

        ValidationEngine.builder(Fixtures.catalog()).build().validate(records, outcome -> {
        });

        assertThat(closed.get()).isEqualTo(1);
    }
}
