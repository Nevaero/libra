# 0018: Synchronous settlement orchestration

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

A settlement instruction must be created from an executed, booked trade. The original design (the
settlement handoff) was event-driven: settlement would consume a `TradeExecuted` event. During
implementation two problems surfaced, so the approach was revised.

## Decision drivers

- Create the instruction atomically with the trade and its booking, so a booked trade is never left
  unscheduled.
- Pass the `bookingEntryId`, which settlement needs in order to settle later.
- Keep the module dependency graph acyclic.
- Keep the command path consistent ([ADR-0009](0009-sync-command-async-fanout.md)).

## Considered options

1. Event-driven choreography: settlement consumes `TradeExecuted`.
2. Synchronous orchestration: trading calls `settlement.scheduleSettlement(...)` in its transaction.

## Decision outcome

Chosen option: **synchronous orchestration.**

Trading, having just created the booking entry, calls
`scheduleSettlement(tradeId, bookingEntryId, tradeDate, assetClass)` in the same transaction.

- Atomicity: the instruction is created in the same transaction as the booking, so there is no
  window where a trade is executed and booked but not scheduled. This is the consistency-boundary
  rule of [ADR-0009](0009-sync-command-async-fanout.md), scheduling shares a boundary with booking,
  so it is synchronous.
- Data: trading has just created the booking entry, so the `bookingEntryId` is in hand. The
  `TradeExecuted` event carries the `Trade` without the `bookingEntryId` settlement needs, so the
  event-driven path had a data gap that would force event bloat or a lookup.
- Acyclicity: trading depends on settlement, and settlement depends only on `{core, ledger}`, so the
  graph stays acyclic. The event-driven path would later need settlement to call back into trading
  to mark the order `SETTLED`, creating a trading-settlement cycle.
- Orchestration versus choreography: trading orchestrates the command path (validation, ledger,
  settlement in sequence), which suits a consistency-critical flow, while choreography suits the
  fan-out at the edges.

The async part is unchanged: the T+2 batch that executes settlement stays scheduled
([ADR-0019](0019-settlement-batch-failure-isolation.md)). Only the scheduling is synchronous. This
decision supersedes the event-driven design described in the settlement handoff.

### Consequences

- Good: a booked trade is always scheduled atomically, with no unscheduled-trade window.
- Good: an acyclic module graph; settlement does not depend on trading.
- Good: the `bookingEntryId` flows directly, with no event bloat or lookup.
- Trade-off: trading is coupled to settlement's port, a deliberate downward dependency.
- Trade-off: the order-to-`SETTLED` transition, when added, becomes the one asynchronous step
  (trading reacting to `TradeSettled` from the batch), a deliberate exception at a real consistency
  boundary.
- Follow-up: implement that `SETTLED` transition in phase 2.

## Pros and cons of the options

### Event-driven choreography
- Good: maximal decoupling.
- Bad: a `bookingEntryId` data gap, an async unscheduled-trade window, and a trading-settlement cycle
  for the `SETTLED` step.

### Synchronous orchestration
- Good: atomic, acyclic, with the data in hand.
- Bad: trading depends on settlement's port.

## Links

- Applies the synchronous command-path rule ([ADR-0009](0009-sync-command-async-fanout.md)).
- The asynchronous T+2 batch ([ADR-0019](0019-settlement-batch-failure-isolation.md)).
- Settles the two-phase booking ([ADR-0010](0010-ledger-two-phase-booking.md)).
