package com.dq.example;

import com.dq.engine.ValidationEngine;
import com.dq.engine.model.DataRecord;
import com.dq.engine.model.Rule;
import com.dq.engine.model.RunSummary;
import com.dq.example.json.JsonRecords;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

/**
 * Validate a batch of records over HTTP.
 *
 * <pre>
 * curl -N -X POST localhost:8080/validate \
 *      -H 'Content-Type: application/json' --data-binary @records.json
 * </pre>
 *
 * <p>The obvious signature — {@code validate(@RequestBody List<...> records)} returning a
 * list — is the one that cannot do the job: Spring would deserialise the whole request
 * before the method is entered, and the return value would hold every outcome. At a million
 * records that exhausts the heap twice before a rule runs.
 *
 * <p>So this takes the raw request stream, parses it incrementally, and writes each outcome
 * as newline-delimited JSON the moment it is produced. Peak memory is one record and one
 * buffer. The last line is the run summary.
 */
@RestController
public class ValidationController {

    private static final String NDJSON = "application/x-ndjson";

    private final List<Rule> catalog;
    private final ObjectMapper mapper;

    public ValidationController(List<Rule> catalog, ObjectMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = NDJSON)
    public void validate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType(NDJSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ValidationEngine engine = ValidationEngine.builder(catalog).build();

        try (OutputStream out = new BufferedOutputStream(response.getOutputStream());
             InputStream body = request.getInputStream();
             Stream<DataRecord> records = JsonRecords.fromArray(body)) {

            NdjsonWriter writer = new NdjsonWriter(mapper, out);

            RunSummary summary = engine.validate(records, writer);
            writer.writeLine(SummaryView.of(summary));
            writer.flush();
        }
    }
}
