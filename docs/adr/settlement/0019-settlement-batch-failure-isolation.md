# 0019: Daily settlement batch with per-instruction failure isolation

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

At T+2 a scheduled batch settles all due instructions. The instructions are independent, since each
trade settles on its own. We must decide the transaction scope of the batch and what happens when
one instruction fails while the rest are fine.

## Decision drivers

- A failing instruction must not block or roll back the others.
- Each instruction's settlement (the pending-to-final transfer) must be atomic on its own.
- The batch outcome must report partial success honestly.
- Settlement execution is the asynchronous, time-shifted step
  ([ADR-0009](../system/0009-sync-command-async-fanout.md)).

## Considered options

1. One transaction for the whole batch (all-or-nothing).
2. One transaction per instruction (`REQUIRES_NEW`), with failures isolated.

## Decision outcome

Chosen option: **one transaction per instruction with `REQUIRES_NEW`, failures isolated.**

The batch loop is not transactional. It iterates the due `PENDING` instructions and calls
`executor.settle(id)`, which runs in `REQUIRES_NEW`. A `RuntimeException` marks that instruction
`FAILED` (also in `REQUIRES_NEW`) and the loop continues. The batch records `COMPLETED` when all
settled and `PARTIAL_FAILURE` when any failed.

This is a bulkhead. Because the instructions are independent, one bad instruction is contained to its
own transaction and cannot sink a day of good settlements. A single batch transaction would roll
back everything on one failure, holding a thousand good trades hostage to one bad one. `REQUIRES_NEW`
gives each instruction an independent transaction that suspends the outer context, so a rollback is
scoped to that instruction.

One implementation detail is worth recording, since it is a classic Spring trap: `settle` and
`markFailed` live in a separate injected bean (`SettlementExecutor`) rather than as methods on the
batch service. Spring's `@Transactional` is proxy-based, so a self-invocation from within the same
bean bypasses the proxy and `REQUIRES_NEW` would silently fail to apply. A separate bean keeps the
proxy in effect. This is the asynchronous half of the settlement split
([ADR-0018](0018-settlement-synchronous-orchestration.md)): scheduling is synchronous, execution is
the scheduled batch.

### Consequences

- Good: one failure is isolated, the rest settle, and the batch reports partial success.
- Good: independent, atomic per-instruction settlement.
- Trade-off: many small transactions instead of one, which is acceptable for a daily batch.
- Trade-off: `FAILED` instructions are terminal in phase 1; reschedule and retry are a later concern.
- Follow-up: a retry or reschedule policy, and metrics on batch outcomes.

## Pros and cons of the options

### One batch transaction
- Good: the simplest control flow.
- Bad: one failure rolls back the entire day's settlements.

### Per-instruction REQUIRES_NEW
- Good: isolated failures and honest partial-success reporting.
- Bad: many transactions, and it needs a separate bean for the transactional proxy to apply.

## Links

- The synchronous scheduling half ([ADR-0018](0018-settlement-synchronous-orchestration.md)).
- The asynchronous, time-shifted batch fits the sync/async rule
  ([ADR-0009](../system/0009-sync-command-async-fanout.md)).
- Settles the two-phase booking from pending to final
  ([ADR-0010](../ledger/0010-ledger-two-phase-booking.md)).
