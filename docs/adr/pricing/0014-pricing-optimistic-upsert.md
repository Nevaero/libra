# 0014: Lock-free optimistic upsert on sequence

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Pricing maintains the latest quote per `(instrument, tenor)`, fed by ticks that arrive every few
milliseconds, possibly out of order (network jitter, multiple sources) and at-least-once (feeds
redeliver). We need correct "latest" semantics at high throughput.

## Decision drivers

- High throughput per instrument (millisecond cadence): no locks or retry loops on the hot path.
- Out-of-order tolerance: a stale tick arriving late must not overwrite a newer one.
- Idempotency: at-least-once feeds redeliver, so replays must not corrupt the latest value.
- A superseded tick is disposable: only the latest value matters.

## Considered options

1. Pessimistic lock per upsert (`SELECT ... FOR UPDATE`).
2. Optimistic concurrency with a version column and retry.
3. A single lock-free conditional upsert keyed on `(instrument, tenor)`, guarded by `sequence`.

## Decision outcome

Chosen option: **a single native conditional upsert guarded by `sequence`.**

```sql
INSERT INTO latest_quotes (...) VALUES (...)
ON CONFLICT (instrument_id, tenor)
DO UPDATE SET ... WHERE latest_quotes.sequence < EXCLUDED.sequence
```

The database resolves the conflict in one atomic statement, so there is no application lock and no
retry loop. It is out-of-order-safe and idempotent by construction: the monotonic `sequence` in the
`WHERE` means only a strictly newer tick wins, while a stale or replayed tick updates zero rows and
is silently dropped. `QuoteService` publishes `QuoteAdvanced` only when a row actually changed, so
downstream consumers see real advances.

The deep reason this works, and the reason it differs from the ledger, is the shape of the data. A
price tick is a snapshot of state: only the latest value matters, older ones are disposable, and
losing one is harmless because the next snapshot carries the whole truth. A posting is a delta, an
event: the balance is the sum of every posting, so each must be counted exactly once, and losing one
corrupts the aggregate permanently. Snapshots admit last-write-wins, discard, and free idempotency,
which is why pricing is a mutable latest-value projection updated lock-free. Deltas demand
conservation, ordering, and exactly-once application, which is why the ledger is immutable,
append-only, and pessimistically locked ([ADR-0010](0010-ledger-two-phase-booking.md),
[ADR-0012](0012-balance-projection-pessimistic-locking.md)).

A COALESCE merge preserves the last traded price across quote-only ticks, so a quote update does not
erase last-trade data for an equity.

### Consequences

- Good: high-throughput, lock-free, out-of-order-safe, idempotent ingestion in one statement.
- Good: no retry logic and no head-of-line blocking.
- Trade-off: requires a trustworthy monotonic `sequence` per key, from the feed or assigned on
  ingest.
- Trade-off: only the latest quote is kept; price history would need a separate time-series store.
- Follow-up: a real transport (the mock-feed) and per-source sequence reconciliation remain.

## Pros and cons of the options

### Pessimistic lock
- Good: correct.
- Bad: head-of-line blocking on the firehose, latency collapse.

### Optimistic with retry
- Good: lock-free.
- Bad: the retries are pointless, since a losing tick should be discarded rather than reapplied.

### Conditional upsert on sequence
- Good: lock-free, idempotent, out-of-order-safe, with no retry.
- Bad: depends on a monotonic sequence per key.

## Links

- The opposite call for the ledger's conserved deltas
  ([ADR-0012](0012-balance-projection-pessimistic-locking.md)).
- At-least-once delivery and idempotent consumers ([ADR-0006](0006-transactional-outbox.md)).
