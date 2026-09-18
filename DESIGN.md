# Design

## Logic and mapping

```
record -> logic -> "ok" / "bad" / "blocked" -> mapping -> VALID / INVALID / REVIEW / NOT_APPLICABLE
```

The logic computes a `String`. A separate table maps that string to a `Decision`.

The mapping always returns something. The builder's last call is `otherwise(...)`, so you
cannot build a mapping without a fallback. That removes "unmapped value" as an error case.

## The API

```java
ValidationEngine engine = ValidationEngine.builder(rules)
        .select(rule -> rule.categories().contains("tax"))
        .batchSize(500)
        .build();

RunSummary summary = engine.validate(records, outcome -> write(outcome));
```

Build an engine, run it. That is the whole surface.

**Outcomes are pushed, not returned.** Returning a list would mean holding 50 million
objects at a million records and 50 rules. Each outcome goes to the handler and is
forgotten.

**`Outcome` is sealed** over `RuleResult` and `RuleError`. Both come through one channel in
the order they happened, so a caller cannot take one and miss the other. A `switch` over
them needs no `default`.

**`RunSummary` is four counters.** It costs the same for ten records or ten million, so it
is safe to return by value.

**The engine is immutable.** Run state lives in `RunCounters`, created inside `validate`.
One engine can serve every request of a web application.

## Fault isolation

One try/catch, around one rule on one record. A failure becomes a `RuleError` holding the
rule id, record id, exception type, message, and the fields already read.

The run continues with the next rule. The caller receives errors and results through the
same handler, so a broken rule cannot hide and cannot stop the batch.

## Memory

Nothing is buffered. `batchSize` controls how often
`OutcomeHandler.flush()` is called, so a streaming host decides when bytes hit the network.

The build enforces this. The library's tests run with `-Xmx256m`, and `LargeVolumeTest`
puts a million records through that heap. Other tests check that outcomes appear after the
first record is read, that the engine closes the stream it was given, and that the JSON
adapter reads under 64 KB before the first record.

A million records against three rules takes about three seconds.

## Rules as data, and the sandbox

`SpelRuleLogic` evaluates a stored SpEL expression. That is what lets a rule live in a
database and change without a build.

It also makes a rule an injection vector, so expressions run in a `SimpleEvaluationContext`
with a single property accessor: no type references, no constructors, no `getClass()`.
Eight tests check those routes stay shut.

## What I would do next

1. **One query per rule, instead of one call per record.** The engine holds one record and
   runs every rule against it, so a million records and fifty rules is fifty million calls.
   When the records already live in a store, each rule can instead run as a single
   set-based query over the whole batch: fifty queries, executed where the data is. That is
   a different engine rather than a tuning of this one, and `RuleLogic` is where it would
   attach. I saw in your job offer RDF and SPARQL so this is probably a way how to do it properly.
2. **Parallel execution.** The engine is already immutable with run state isolated. Three
   seconds for a million records did not justify the concurrency surface yet.

