package com.dq.example.json;

import com.dq.engine.model.DataRecord;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.type.MapType;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Adapts a JSON array into a lazy stream of {@link DataRecord}s. The reference
 * implementation of that seam: what bytes a record arrives as is the host's business, so
 * the library has no JSON library of its own. This host runs Jackson 3, because that is
 * what Spring Boot 4 ships.
 *
 * <p>The one-line alternative,
 * {@code mapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {})},
 * materialises the whole batch before a single rule runs. Here a {@link JsonParser} walks
 * the array one element at a time, so peak memory is one record whatever the input size.
 *
 * <p>The returned stream must be closed, which closes the parser and the underlying input.
 * {@link com.dq.engine.ValidationEngine#validate} does that for you.
 */
public final class JsonRecords {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MapType FIELD_MAP_TYPE =
            MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);

    /**
     * Reads one record object from a parser that is part-way through an array.
     *
     * <p>{@code FAIL_ON_TRAILING_TOKENS} has to be off, and this is a genuine Jackson 3
     * behaviour change: it is enabled by default there, making the mapper treat "there is
     * more input after this value" as an error. That is right for reading a whole document
     * and wrong for reading the 400,000th element of an array — the rest of the array is
     * not trailing junk, it is the next records.
     */
    private static final ObjectReader RECORD_READER =
            MAPPER.readerFor(FIELD_MAP_TYPE).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonRecords() {
    }

    /**
     * Streams records from a JSON array of objects: {@code [ {...}, {...} ]}.
     *
     * @param input the JSON, consumed lazily; closed when the stream is closed
     * @throws IllegalArgumentException if the input is not a JSON array
     */
    public static Stream<DataRecord> fromArray(InputStream input) {
        Objects.requireNonNull(input, "input");

        JsonParser parser = MAPPER.createParser(input);
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            parser.close();
            throw new IllegalArgumentException("expected a JSON array of records at the top level");
        }

        return StreamSupport.stream(new RecordSpliterator(parser, RECORD_READER), false)
                .onClose(parser::close);
    }
}
