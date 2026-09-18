# Data-Quality Rule Engine

A reusable Java library that validates business-partner master data against a
catalog of data-quality rules.

A rule computes a **value** from a record's fields; a declarative **mapping** turns
that value into a **decision** (`VALID` / `INVALID` / `REVIEW` / `NOT_APPLICABLE`).
Keeping those apart is what lets a data steward change policy by editing data
instead of asking a developer to change code.

| module | |
|---|---|
| `dq-engine` | the library — 22 classes, ~490 lines, one dependency (`spring-expression`) |
| `dq-example-api` | a Spring Boot host that embeds it, plus the JSON adapters |

## Build and test

Needs JDK 21 or later. No Gradle installation — use the wrapper.

```bash
./gradlew build              # compile + run all 61 tests
./gradlew :dq-engine:test    # library only
```

The library's test JVM runs with `-Xmx256m` on purpose: `LargeVolumeTest` pushes a
million records through that heap, so bounded memory is enforced by the build.

## Using it

```java
ValidationEngine engine = ValidationEngine.builder(rules)   // List<Rule>
        .select(rule -> rule.categories().contains("tax"))
        .batchSize(500)
        .build();

try (Stream<DataRecord> records = myRecordSource()) {
    RunSummary summary = engine.validate(records, outcome -> switch (outcome) {
        case RuleResult result -> report.write(result);
        case RuleError error   -> alerts.raise(error);
    });
}
```

The engine is immutable and thread-safe. Outcomes are pushed to the handler as they
are produced and never accumulated.

You supply the catalog as a `List<Rule>` — in production a MariaDB query — and
records as `DataRecord`, over JSON, Avro, a `ResultSet` row or a `Map`.
`MapDataRecord` is shipped; `dq-example-api/.../json/JsonRecords.java` is a
streaming JSON implementation to copy.

## Writing a rule

A rule is data:

```json
{
  "id": "vatFormat",
  "label": "VAT id is present and long enough",
  "status": "RELEASED",
  "severity": "ERROR",
  "country": "WORLD",
  "categories": ["tax"],
  "value": "vatId != null and vatId.length() >= 9 ? 'ok' : 'bad'",
  "mapping": {
    "cases": { "ok": "VALID", "bad": "INVALID" },
    "default": "REVIEW"
  }
}
```

`value` is a SpEL expression evaluated in a sandbox that blocks type references,
constructors, bean references and `getClass()`. `country` may be an ISO code, or
`WORLD`/omitted. `default` is mandatory. Only `RELEASED` rules ever run.

## Running the example API

```bash
./gradlew :dq-example-api:bootRun

curl -N -X POST localhost:8080/validate \
     -H 'Content-Type: application/json' \
     --data-binary '[
       {"id":"r1","vatId":"DE111111111","country":"DE","legalName":"ACME GmbH","iban":"DE123456789"},
       {"id":"r2","vatId":"FR22","country":"FR","legalName":"Bricolage SARL","iban":"FR123456789"},
       {"id":"r3","country":"ZZ","legalName":"Nowhere Ltd","iban":""}
     ]'
```

The response is newline-delimited JSON, streamed as results are produced, with the
summary last:

```json
{"type":"result","ruleId":"vatFormat","recordId":"r2","decision":"INVALID",
 "severity":"ERROR","computedValue":"bad","provenance":[{"field":"vatId","value":"FR22"}]}
...
{"type":"summary","resultCount":10,"errorCount":0,
 "byDecision":{"NOT_APPLICABLE":2,"VALID":4,"REVIEW":0,"INVALID":4},
 "bySeverity":{"ERROR":6,"INFO":0,"WARNING":4}}
```

NDJSON rather than a JSON array because the response is produced incrementally: a
truncated array is unparseable and loses everything, whereas every complete NDJSON
line already received is still usable.

The demo catalog also carries a `DE`-scoped rule and a `DRAFT` one, so a single call
shows country scope and the RELEASED-only invariant.

See [DESIGN.md](DESIGN.md) for the reasoning.
