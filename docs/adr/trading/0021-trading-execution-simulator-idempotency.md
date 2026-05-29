# 0021: In-memory execution simulator and idempotent submission

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Trading must execute an accepted order into a trade, and it must handle clients that retry a
submission. Phase 1 has no real execution venue. We must decide how execution is simulated, how
submission stays exactly-once, and how the order itself is modelled.

## Decision drivers

- Phase 1 needs a working execution path with no real venue.
- The execution step must be replaceable by a real venue later without touching the orchestrator.
- A client may retry `submitOrder` (network jitter, at-least-once delivery), and a retry must not
  place a second order.
- Keep the order model simple for phase 1.

## Considered options

- Execution: a real venue now, or an in-memory simulator behind a seam.
- Order model: a sealed `MarketOrder | LimitOrder` hierarchy, or a single `Order` record with an
  `OrderType` enum.

## Decision outcome

Chosen: **an in-memory `ExecutionSimulator` behind a port, client-supplied idempotency keys, and a
single `Order` record.**

Execution: the `ExecutionSimulator` fills the full quantity at the current quote, the ask for a BUY
and the bid for a SELL. A LIMIT order fills only if the market has crossed (ask at or below the limit
for a BUY, bid at or above it for a SELL), otherwise there is no fill and the order is `CANCELLED`.
There are no partial fills or slippage in phase 1. The simulator sits behind a clear seam, so a real
venue (partial fills, asynchronous execution, slippage) can replace it without changing the
orchestrator.

Idempotency: `submitOrder` is idempotent on `(clientId, idempotencyKey)`, backed by a database UNIQUE
constraint. A replayed submission resolves to the original order rather than creating a second one.
That gives exactly-once submission against at-least-once clients, the same idempotency discipline at
the command boundary that the outbox applies to events ([ADR-0006](../system/0006-transactional-outbox.md)).

Order model: a single `Order` record discriminated by an `OrderType` enum (`MARKET` or `LIMIT`) with
a nullable limit price, rather than a sealed `MarketOrder | LimitOrder` hierarchy. For two variants
that share nearly all fields, one record with a guarded invariant (limit price non-null exactly when
`LIMIT`) is simpler and maps cleanly to one table. A sealed hierarchy would pay off only with more
divergent variants.

### Consequences

- Good: a working phase-1 execution path, replaceable by a real venue behind the seam.
- Good: exactly-once submission, with no duplicate orders on retry.
- Good: a simple order model that maps to one table.
- Trade-off: the simulator is unrealistic (full fills, no slippage), acceptable for phase 1.
- Trade-off: the single record carries a nullable limit price guarded by an invariant, instead of the
  type system distinguishing the two variants.
- Follow-up: a real execution venue, partial fills, and the multi-leg `ParentOrder` path in phase 2.

## Pros and cons of the options

### Real venue now
- Good: realistic execution.
- Bad: out of scope and heavy for phase 1.

### In-memory simulator behind a seam
- Good: working, testable, and replaceable.
- Bad: not realistic yet.

### Sealed Market/Limit hierarchy
- Good: the type system distinguishes the variants.
- Bad: more types for little gain when the variants share nearly all fields.

### Single Order record with OrderType
- Good: simple, one table.
- Bad: a nullable limit price guarded by an invariant rather than by the type system.

## Links

- Feeds the DvP booking ([ADR-0020](0020-trading-dvp-booking.md)).
- Idempotency mirrors the outbox's idempotent-consumer discipline
  ([ADR-0006](../system/0006-transactional-outbox.md)).
- Phase-1 scope; multi-leg and a real venue are phase 2.
