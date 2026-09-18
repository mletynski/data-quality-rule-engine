package com.dq.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end through the example host, over real HTTP on a real servlet container.
 *
 * <p>Deliberately the JDK's own {@link HttpClient} rather than a mock MVC harness: the
 * endpoint's whole point is that it streams, and a mock harness would happily pass while
 * buffering the entire response in memory.
 *
 * <p>Its second job is to check that the library is pleasant to embed. Wiring the engine
 * into a controller took one constructor argument and one builder call; had it taken forty
 * lines of setup, that would be a defect in {@code dq-engine}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ValidationApiTest {

    private static final String RECORDS = """
            [
              {"id":"r1","vatId":"DE111111111","country":"DE","legalName":"ACME GmbH","iban":"DE123456789"},
              {"id":"r2","vatId":"FR22","country":"FR","legalName":"Bricolage SARL","iban":"FR123456789"},
              {"id":"r3","country":"ZZ","legalName":"Nowhere Ltd","iban":""}
            ]
            """;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ObjectMapper mapper;

    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * The task's table, asserted through HTTP. countryBlocked, vatFormat and ibanFormat
     * apply everywhere; germanVatPrefix is scoped to DE, so it touches r1 only.
     */
    @Test
    @DisplayName("POST /validate streams one JSON line per outcome, then the summary")
    void validatesABatch() throws Exception {
        List<JsonNode> lines = postRecords(RECORDS);

        JsonNode summary = lines.getLast();
        assertThat(summary.get("type").asString()).isEqualTo("summary");
        assertThat(summary.get("errorCount").asLong()).isZero();
        assertThat(summary.get("byDecision").get("INVALID").asLong()).isEqualTo(4);

        assertThat(decisionsByCell(lines)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("r1/countryBlocked", "NOT_APPLICABLE"),
                Map.entry("r1/vatFormat", "VALID"),
                Map.entry("r1/ibanFormat", "VALID"),
                Map.entry("r1/germanVatPrefix", "VALID"),
                Map.entry("r2/countryBlocked", "NOT_APPLICABLE"),
                Map.entry("r2/vatFormat", "INVALID"),
                Map.entry("r2/ibanFormat", "VALID"),
                Map.entry("r3/countryBlocked", "INVALID"),
                Map.entry("r3/vatFormat", "INVALID"),
                Map.entry("r3/ibanFormat", "INVALID")));
    }

    /** F6 through HTTP: a DE-scoped rule runs on the German record and on no other. */
    @Test
    @DisplayName("a country-scoped rule runs only on records of that country")
    void countryScopeIsVisibleOverHttp() throws Exception {
        assertThat(decisionsByCell(postRecords(RECORDS)))
                .containsKey("r1/germanVatPrefix")
                .doesNotContainKeys("r2/germanVatPrefix", "r3/germanVatPrefix");
    }

    /** F6 through HTTP: the catalog's DRAFT rule is never executed. */
    @Test
    @DisplayName("the DRAFT rule in the catalog never runs")
    void draftRulesNeverRun() throws Exception {
        assertThat(decisionsByCell(postRecords(RECORDS)).keySet())
                .noneSatisfy(cell -> assertThat(cell).contains("legalNameNotEmpty"));
    }

    @Test
    @DisplayName("results carry provenance and the computed value over the wire")
    void outcomesExplainThemselves() throws Exception {
        JsonNode vatOnR2 = postRecords(RECORDS).stream()
                .filter(line -> "r2".equals(line.path("recordId").asString(""))
                        && "vatFormat".equals(line.path("ruleId").asString("")))
                .findFirst()
                .orElseThrow();

        assertThat(vatOnR2.get("decision").asString()).isEqualTo("INVALID");
        assertThat(vatOnR2.get("severity").asString()).isEqualTo("ERROR");
        assertThat(vatOnR2.get("computedValue").asString()).isEqualTo("bad");
        assertThat(vatOnR2.get("provenance").get(0).get("field").asString()).isEqualTo("vatId");
        assertThat(vatOnR2.get("provenance").get(0).get("value").asString()).isEqualTo("FR22");
    }

    private List<JsonNode> postRecords(String body) throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/validate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/x-ndjson");

        List<JsonNode> lines = new ArrayList<>();
        for (String line : response.body().split("\n")) {
            if (!line.isBlank()) {
                lines.add(mapper.readTree(line));
            }
        }
        return lines;
    }

    private static Map<String, String> decisionsByCell(List<JsonNode> lines) {
        Map<String, String> decisions = new LinkedHashMap<>();
        for (JsonNode line : lines) {
            if ("result".equals(line.path("type").asString(""))) {
                decisions.put(line.get("recordId").asString() + "/" + line.get("ruleId").asString(),
                        line.get("decision").asString());
            }
        }
        return decisions;
    }
}
