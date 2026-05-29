# 0016: Regulatory lifecycle as a guarded state machine

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

A brokerage client follows a regulated lifecycle. KYC (Know Your Customer), the legal obligation to
verify identity before trading, must clear before a client becomes active, and some transitions are
illegal: a client cannot be activated without KYC, and a closed account cannot be reopened. We must
model the lifecycle so these compliance rules hold and live in one place.

## Decision drivers

- Regulatory correctness: a client must never reach a state compliance forbids, such as active
  without KYC.
- A single source of truth for the transition rules.
- The rules must not be bypassable by a stray status assignment.
- Lifecycle changes must be auditable.

## Considered options

1. A free-form `status` field set wherever needed.
2. An explicit guarded state machine in `CustomerService`.
3. A dedicated workflow engine.

## Decision outcome

Chosen option: **an explicit guarded state machine in `CustomerService`.**

The states are `PENDING_KYC`, `ACTIVE`, `SUSPENDED`, and `CLOSED` (terminal), with transitions
declared as allowed-from sets and activation guarded on the KYC level not being `NONE`. An undeclared
transition throws.

A free `status` field would scatter the rules across every caller and allow illegal transitions. The
explicit machine keeps the rules in one place, the single source of truth. Because every transition
goes through the port and is checked against the declared graph, the set of reachable states is
closed: an illegal state such as active-without-KYC or a reopened closed account is unreachable, so
no path leads there. This is the make-illegal-states-unrepresentable principle of
[ADR-0011](../ledger/0011-double-entry-invariant-at-construction.md) applied to the lifecycle rather than to a
single object's invariant, which turns compliance from a convention people must remember into
something structural.

Funnelling transitions through one method also gives a natural place to emit the lifecycle events
(activated, suspended, closed) for the regulatory audit trail, so auditability comes for free. A
workflow engine would be heavyweight external machinery for four states.

### Consequences

- Good: compliance rules are enforced in one place and cannot be bypassed; illegal transitions are
  impossible.
- Good: a clear, auditable lifecycle with an event at every transition.
- Trade-off: transitions must go through the service, with no direct status edits, which is intended.
- Trade-off: adding a state or transition means updating the declared graph, which is cheap and
  explicit.
- Follow-up: the same explicit-state-machine shape recurs in `reference` (instrument lifecycle) and
  `trading` (order status).

## Pros and cons of the options

### Free status field
- Good: trivial to write.
- Bad: scattered rules, illegal transitions, and no single source of truth.

### Explicit guarded state machine
- Good: a single source of truth, guarded, unbypassable, and auditable.
- Bad: transitions only through the service.

### Workflow engine
- Good: powerful and visual.
- Bad: overkill for four states.

## Links

- The same principle as the ledger invariant
  ([ADR-0011](../ledger/0011-double-entry-invariant-at-construction.md)), applied to a lifecycle.
- Mirrors the instrument lifecycle ([ADR-0013](../core/0013-security-master-extraction.md)).
- Lifecycle events flow through the outbox ([ADR-0006](../system/0006-transactional-outbox.md)).
