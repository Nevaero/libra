# 0006: Transactional outbox for all events

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Events carry Libra's cross-cutting concerns (audit, projections, future external integrations) and
keep modules decoupled. Publishing to Kafka and committing the business change are writes to two
different systems with no shared transaction. Done naively (commit, then publish) a crash in
between either loses the event or, in the reverse order, emits a phantom event for a change that
rolled back. This is the dual-write problem, and for a ledger a lost or phantom event is a
correctness bug.

## Decision drivers

- Atomicity: an event and the state change it describes must commit together or not at all.
- Resilience: a crash or a Kafka outage must not lose events or emit phantoms.
- No distributed transaction: avoid a two-phase commit between the database and the broker.
- Auditability: a durable record of every event emitted.
- A clean foundation for the modular monolith's internal and external eventing.

## Considered options

1. Direct publish to Kafka inside the handler (dual write).
2. Transactional outbox via Spring Modulith.
3. Change Data Capture (Debezium reading the write-ahead log).

## Decision outcome

Chosen option: **transactional outbox via Spring Modulith.**

The event is written to the `event_publication` table in the same database transaction as the
business change, so the two commit together. A rollback drops the event, which removes phantom
events; a commit guarantees the event is recorded, which removes lost events. This replaces a
distributed two-phase commit across database and broker with a single local transaction plus an
asynchronous relay.

The relay ships to Kafka after commit and retries on failure, which gives at-least-once delivery.
The consequence is that every consumer must be idempotent: dedupe on the event id, or use naturally
idempotent operations such as upserts. Libra already applies this principle (the balance projection
keys on `lastPostingId`, pricing's upsert is conditional on `sequence`).

Ordering holds per aggregate, with a caveat. The outbox relays in commit order, and Kafka preserves
order within a partition, so routing one aggregate's events to a stable partition key keeps
per-entity order. There is no global total order across partitions, and consumers must not assume
one.

`event_publication` doubles as a durable log of what was emitted and what completed, which serves as
an audit trail and allows re-publishing incomplete events. Events are published through
`ApplicationEventPublisher` inside the transaction, never by a direct Kafka producer call.

### Consequences

- Good: no lost or phantom events; eventing is atomic with state, with no 2PC.
- Good: a built-in audit log of emitted events and a replay path.
- Trade-off: at-least-once delivery pushes idempotency onto every consumer.
- Trade-off: the relay adds a little latency between commit and external delivery, so the edges are
  eventually consistent.
- Trade-off: `event_publication` needs housekeeping for completed rows, and is materialized in
  Flyway because Spring Modulith 2.x no longer creates it automatically.
- Follow-up: choose partition keys per topic so the per-aggregate ordering guarantee lands where it
  matters.

## Pros and cons of the options

### Direct publish to Kafka
- Good: simplest, no extra table.
- Bad: the dual-write race loses events or emits phantoms; unacceptable for a ledger.

### Transactional outbox (Spring Modulith)
- Good: atomic with the business transaction, resilient, auditable, no 2PC.
- Bad: at-least-once needs idempotent consumers, and the relay adds slight latency.

### Change Data Capture (Debezium)
- Good: decoupled from application code, captures every committed change.
- Bad: heavy infrastructure for this project, and couples the stream to table schemas.

## Links

- Every domain event flows through `ApplicationEventPublisher`; Spring Modulith externalizes to Kafka.
- `event_publication` is materialized in `V1__schema.sql`.
- Idempotent consumption relates to the balance projection and to pricing's sequence-conditional
  upsert ([ADR-0014](../pricing/0014-pricing-optimistic-upsert.md)).
