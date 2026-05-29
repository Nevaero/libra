# 0012: Pessimistic locking on the Balance projection

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

When a posting hits an account, the `Balance` projection (book, available, pending) must update.
Concurrent postings to the same account race, and the ledger has to stay correct and immediately
consistent. We need a concurrency strategy for the balance row, and it should match the workload
rather than be applied by reflex.

## Decision drivers

- Money needs read-your-writes consistency: a committed posting's balance effect must be visible
  immediately, with no eventual-consistency window.
- Correctness under concurrent postings to the same account.
- Predictable behaviour under contention, with no retry storms or starvation.
- Every posting is money that must be applied; none can be discarded.

## Considered options

1. No locking (last write wins).
2. Optimistic control (a version column with retry on conflict).
3. Pessimistic control (`SELECT ... FOR UPDATE` on the balance row), in the posting transaction.

## Decision outcome

Chosen option: **update the Balance in the same transaction as the posting, with a pessimistic row
lock.**

The projection is synchronous: a committed posting and its balance update are atomic, so
read-your-writes holds and nobody can double-spend in a lag window. The `BalanceProjector` is
package-private and called only by `PostingService` inside the posting transaction.

The concurrency choice is driven by contention rate times the cost of a conflict, and the cost is
asymmetric. A hot ledger account sees a handful of postings a minute, and every posting is money
that must be applied; none can be dropped. Under optimistic control a conflict means
retry-until-success, and those retries storm under contention. A pessimistic row lock instead
serializes the few writers for a short critical section (read, apply, write), with negligible wait
at that rate and no retry logic. The lock is row-level, so two different accounts never block each
other.

The contrast with pricing is deliberate. A price instrument receives a tick every few
milliseconds, and a stale tick can simply be discarded because the next one supersedes it.
Pessimistic locking on that firehose would queue every writer behind the lock (head-of-line
blocking, latency collapse), so pricing uses a lock-free conditional upsert keyed on a monotonic
sequence that drops stale writes ([ADR-0014](../pricing/0014-pricing-optimistic-upsert.md)). Same system, two
strategies, each matched to its workload: high throughput with a discardable conflict points to
optimistic, low throughput with a must-apply conflict points to pessimistic.

### Consequences

- Good: balances are strongly consistent and correct under concurrency, with no retry code.
- Good: row-level locking, so different accounts do not contend with each other.
- Trade-off: a writer waits for the lock, acceptable because the rate per account is low and the
  critical section is short.
- Trade-off: the synchronous projection adds work to the posting transaction, with no async offload.
- Follow-up: if an account ever became a genuine write hotspot, this is the place to revisit (shard
  the balance, or switch to an append-only accumulator).

## Pros and cons of the options

### No locking
- Good: fastest.
- Bad: corrupts the balance under concurrency.

### Optimistic with retry
- Good: lock-free, ideal under low conflict.
- Bad: a posting cannot be discarded, so conflicts mean retries, which storm under contention.

### Pessimistic in the same transaction
- Good: correct, predictable, and retry-free.
- Bad: a writer waits, and the projection runs inside the posting transaction.

## Links

- Pricing makes the opposite call for its workload
  ([ADR-0014](../pricing/0014-pricing-optimistic-upsert.md)).
- The balance derives available from pending ([ADR-0010](0010-ledger-two-phase-booking.md)).
- `BalanceProjector` stays internal to the ledger
  ([ADR-0002](../system/0002-named-interface-boundaries.md)).
