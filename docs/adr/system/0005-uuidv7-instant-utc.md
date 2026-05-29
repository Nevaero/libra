# 0005: UUIDv7 identifiers and Instant UTC timestamps

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Every entity needs an identifier, and most carry timestamps. We want identifiers that a module can
generate before persistence (no shared sequence, no coordination, safe to merge across modules and
any future shard) without wrecking database index locality, and that resist enumeration when
exposed. We also want a timestamp representation that denotes one unambiguous instant regardless of
server timezone, with the right type for civil dates that have no time of day.

## Decision drivers

- Coordination-free id generation: assign an id in the domain before the insert, with no round-trip
  and no shared sequence, which fits the outbox and event model.
- Index locality: keys should insert in order so B-tree writes stay cheap.
- Unguessability: identifiers that reach a client must not be enumerable.
- Unambiguous time: a stored timestamp must denote a single instant independent of timezone or DST.
- Right type for the concept: a point in time versus a civil calendar date.

## Considered options

Identifiers:

1. Auto-increment `BIGINT`.
2. UUIDv4 (random).
3. UUIDv7 (time-ordered, RFC 9562).

Timestamps:

- `Instant` (UTC) versus `LocalDateTime`, with `LocalDate` for civil dates.

## Decision outcome

Chosen: **UUIDv7 for every identifier, `Instant` (UTC) for points in time, `LocalDate` for civil
dates.**

UUIDv7 combines the two properties we want. It is coordination-free, so any module mints an id
without a sequence or a round-trip and merges cleanly, and it is index-friendly, because the 48-bit
millisecond timestamp prefix makes new rows sort to the right of the B-tree the way an
auto-increment does. About 74 bits of randomness keep each id unguessable.

On security, the larger property is that UUIDs remove enumeration. An auto-increment key leaks its
neighbours (id `N` implies `N-1` and `N+1`), the classic setup for an Insecure Direct Object
Reference; the randomness in UUIDv7 blocks that entire class. The one thing v7 leaks is creation
time, and for Libra that is low severity: it cannot be turned into an auth bypass or an
enumeration, and the only realistic concern is information disclosure if an internal id is exposed
externally (business intelligence from collected ids, or timing correlation against other logs).
If a specific resource ever needs creation-time privacy, it can carry a separate opaque public id.

For time, `Instant` represents any moment that happened: an event's `occurredAt` and `receivedAt`,
plus `createdAt`, `submittedAt`, `executedAt`, `settledAt`, and a quote's `quoteTime`. It is a
single UTC instant, immune to server timezone and DST. `LocalDateTime` is banned because it is a
wall-clock reading with no zone. `LocalDate` is used where the time of day is meaningless: a
settlement value date, a trade date, a holiday calendar date, a customer birth date.

### Consequences

- Good: ids are generated anywhere with no contention, stay index-friendly and unguessable, and
  sort by creation time, which helps pagination and debugging.
- Good: timestamps are unambiguous, so no timezone or DST bugs on the command path.
- Trade-off: 16 bytes per id against 8 for a `BIGINT`, and the creation time is embedded.
- Trade-off: a developer must choose `Instant` or `LocalDate` deliberately for each field.
- Follow-up: if an externally exposed id ever needs creation-time privacy, add an opaque public id
  for that resource.

## Pros and cons of the options

### Auto-increment BIGINT
- Good: tightest index, 8 bytes, simple.
- Bad: needs a round-trip, is enumerable (IDOR), and is awkward to merge or shard.

### UUIDv4
- Good: coordination-free and unguessable.
- Bad: random keys scatter inserts and hurt B-tree locality.

### UUIDv7
- Good: coordination-free, unguessable, and index-friendly through the time-ordered prefix.
- Bad: 16 bytes, and embeds creation time.

## Links

- `Uuids.newId()` in `util` generates every aggregate id.
- `Instant` is used across the domain records; `LocalDate` carries value and trade dates in
  `settlement` (see the `BusinessDayCalculator` and [ADR-0019](../settlement/0019-settlement-batch-failure-isolation.md)).
