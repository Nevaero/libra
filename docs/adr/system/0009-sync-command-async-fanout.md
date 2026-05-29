# 0009: Synchronous command path, asynchronous fan-out

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

A trade runs `submitOrder` -> validation -> execution -> ledger booking -> settlement scheduling.
We must decide which of those steps are direct in-transaction calls and which are events, and
separately what genuinely has to be asynchronous (the T+2 delay, the inbound price feed, the
edge consumers such as audit and projections).

## Decision drivers

- The command needs an authoritative result returned to the caller (`EXECUTED` / `REJECTED` /
  `CANCELLED`).
- The consistency-critical steps (order, booking, settlement instruction) must be atomic.
- The edges (audit, projections, external integrations) must not block or couple to the command.
- Some work is inherently time-shifted (the T+2 batch) or external (the price stream).
- Avoid the operational weight and eventual-consistency reasoning of event-coupling the core path.

## Considered options

1. Fully synchronous, including the fan-out.
2. Fully event-driven, every step triggered by the previous step's event.
3. Hybrid: a synchronous command path with asynchronous fan-out, batch, and ingestion.

## Decision outcome

Chosen option: **the hybrid.**

The governing rule is simple. Synchronous means "do this now and tell me the result"
(request/response); asynchronous means "this happened, react if you care" (publish/subscribe), the
difference between a restaurant order and a phone notification. A step is synchronous when the
caller needs an authoritative answer or shares a consistency boundary with it, and asynchronous
when it is a fact others can act on independently, with no consistency coupling to the originating
transaction. Put another way, the consistency boundary is the transaction boundary is the
synchronous boundary: inside one, stay synchronous; past the commit, go asynchronous.

Applied to Libra, the command path is one synchronous ACID transaction, because the client needs
the order outcome immediately and because the order, the DvP booking, and the settlement
instruction must commit together. Past the commit everything is asynchronous: the outbox fans
events out to the edges, the T+2 settlement batch is time-triggered (a process cannot block for two
days), and the inbound price stream is an external push.

Fully synchronous would drag the edges and external pushes into the command, making it slow and
brittle. Fully event-driven would split the consistency-critical steps across transactions, forcing
sagas, compensation, eventual consistency on the order result, and idempotent handlers everywhere,
plus the operational weight of topics, partitions, and a hard Kafka dependency on the critical
path. That is a distributed monolith's cost without a distributed system's need.

### Consequences

- Good: the command path is strongly consistent and returns an authoritative result, with no saga
  or compensation logic.
- Good: the edges, the batch, and ingestion scale and fail independently of the command.
- Trade-off: the command path has temporal coupling (the callee must be up and the caller waits),
  accepted because the steps are in-process and fast.
- Trade-off: the edges are eventually consistent, so a projection lags the commit slightly.
- Follow-up: the order-to-`SETTLED` transition is the one place that crosses into async later
  (trading reacts to a `TradeSettled` event from the batch), a deliberate exception at a real
  consistency boundary.

## Pros and cons of the options

### Fully synchronous
- Good: the simplest result handling.
- Bad: couples the edges to the command, which becomes slow and fragile.

### Fully event-driven
- Good: maximal decoupling and independent scaling.
- Bad: sagas, eventual consistency, idempotency everywhere, and Kafka on the critical path. A
  distributed monolith's cost.

### Hybrid
- Good: a consistent core and decoupled edges.
- Bad: two models to understand and a boundary to keep clear.

## Links

- The asynchronous fan-out mechanism is the outbox ([ADR-0006](0006-transactional-outbox.md)).
- Synchronous settlement orchestration keeps the command path acyclic
  ([ADR-0018](../settlement/0018-settlement-synchronous-orchestration.md)).
- The modular monolith makes in-process synchronous calls cheap
  ([ADR-0001](0001-modular-monolith.md)).
