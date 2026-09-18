package com.dq.example;

import com.dq.engine.OutcomeHandler;
import com.dq.engine.model.Outcome;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * Writes each outcome to the response as one JSON line. The engine's batching drives the
 * flushes.
 *
 * <p>A write failure means the client hung up. It is allowed to propagate, which ends the
 * run promptly rather than evaluating another million records nobody is listening to.
 */
final class NdjsonWriter implements OutcomeHandler {

    private final ObjectMapper mapper;
    private final OutputStream out;

    NdjsonWriter(ObjectMapper mapper, OutputStream out) {
        this.mapper = mapper;
        this.out = out;
    }

    @Override
    public void handle(Outcome outcome) {
        writeLine(OutcomeView.of(outcome));
    }

    @Override
    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("could not flush the response", e);
        }
    }

    /** Also used for the summary, which is the last line of the response. */
    void writeLine(Object view) {
        try {
            out.write(mapper.writeValueAsBytes(view));
            out.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException("could not write to the response", e);
        }
    }
}
