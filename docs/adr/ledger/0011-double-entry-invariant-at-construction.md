# 0011: Validate the double-entry invariant at construction

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

The double-entry invariant is the ledger's foundational rule: for every `JournalEntry`, and for each
asset in it, `sum(DEBIT) == sum(CREDIT)`. Nothing appears or disappears, because every movement has
a counterparty posting. We must decide where this is enforced so that it can never be violated by
any code path, present or future.

## Decision drivers

- The invariant must be impossible to violate, not merely checked on the happy path.
- Fail as early as possible, before persistence and before any event is published.
- Centralize enforcement so every caller benefits with no extra code.
- Prove the rule rigorously rather than with a few examples.

## Considered options

1. Validate in the service layer before saving.
2. Validate with a database constraint or trigger.
3. Validate in the compact constructor of the `JournalEntry` record.

## Decision outcome

Chosen option: **validate in the `JournalEntry` compact constructor.**

This makes illegal states unrepresentable: an unbalanced `JournalEntry` cannot be constructed, so no
code path can produce one. The invariant is intrinsic to the type rather than a separate validator a
new caller might forget. It is the "parse, don't validate" principle: construction parses raw
postings into a type that guarantees balance, and every downstream consumer can trust it without
re-checking. It fails fast, before persistence and before the `JournalEntryPosted` event, so an
invalid entry never reaches the database or the outbox.

This is only possible because the domain is a record ([ADR-0007](0007-anti-corruption-layer.md)); a
JPA entity with a forced no-arg constructor could not guarantee balance at construction. By
contrast, a service-layer check lives outside the object and is bypassable by a direct
construct-and-save, and a database constraint expresses "balanced per asset across N rows" awkwardly,
fails late at flush, and still allows an unbalanced object to exist in memory.

The rule is proven by the project's signature test: a jqwik property-based test asserts that balanced
posting sets construct and unbalanced ones throw, across many generated cases rather than a handful
of examples.

### Consequences

- Good: the most important rule cannot be violated; correctness holds by construction.
- Good: every caller is protected with zero additional code.
- Good: a rigorous, generative proof of the invariant.
- Trade-off: the validation logic lives in the domain record, which is appropriate since it is a
  domain rule.
- Trade-off: the constructor does real work (summing per asset), a small cost on every entry.
- Follow-up: a database CHECK could be added later as defense in depth, but the type stays the
  authoritative guard.

## Pros and cons of the options

### Service-layer validation
- Good: simple to write.
- Bad: extrinsic to the object and bypassable by a direct construct-and-save.

### Database constraint or trigger
- Good: enforced at storage.
- Bad: awkward to express, fails late, and still allows an invalid in-memory object.

### Compact-constructor validation
- Good: intrinsic, fail-fast, and impossible to bypass.
- Bad: the rule lives in the domain record, which is the right place anyway.

## Links

- Enabled by domain records ([ADR-0007](0007-anti-corruption-layer.md)).
- Underpins two-phase booking and the DvP entry ([ADR-0010](0010-ledger-two-phase-booking.md),
  [ADR-0020](0020-trading-dvp-booking.md)).
