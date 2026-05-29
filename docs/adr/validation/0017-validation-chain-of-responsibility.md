# 0017: Chain of Responsibility, collect-all

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Before a trade executes it must pass several independent pre-trade checks: customer active, KYC
sufficient, instrument tradable, funds sufficient, limit price sane. We must structure these checks
and decide what happens when one fails, in a regulated setting where rejections are audited.

## Decision drivers

- Each check is independent and should be testable and composable in isolation.
- Adding or removing a rule should not touch the others.
- A rejection must produce a complete, auditable record of every reason, which is a regulatory
  requirement.
- Good client feedback, ideally every problem reported at once.

## Considered options

Structure:

1. One large branchy validation method.
2. A Chain of Responsibility of independent rules.
3. A dedicated rules engine.

Failure handling:

- Fail-fast (stop at the first failure).
- Collect-all (run every rule and aggregate the failures).

## Decision outcome

Chosen option: **a Chain of Responsibility with collect-all.**

Each rule is an independent predicate over a `ValidationContext`, composed into a list. That gives
Single Responsibility per rule, Open-Closed for the chain (a new rule is added without touching the
rest), and isolated unit tests per rule. A large branchy method would violate both principles, and a
rules engine is overkill for five rules.

Collect-all over fail-fast is decided by compliance. A pre-trade rejection in a regulated broker
needs a complete record of every reason it failed, carried on the `ValidationFailed` event for the
audit, which is non-negotiable. Fail-fast would record only the first reason. The secondary benefit
is client experience: the client sees every problem at once rather than fixing one, resubmitting,
and hitting the next. This is the Notification pattern: accumulate results into `Approved` or
`Rejected(reasons)` instead of throwing on the first failure.

Collect-all is safe here because the rules are independent and side-effect-free reads over a
pre-built context, so running all of them after one has failed causes no harm, and the extra cost is
trivial since the checks are cheap reads.

### Consequences

- Good: a complete, auditable rejection record, better client feedback, and extensible isolated
  rules.
- Good: the chain's composition and order are explicit.
- Trade-off: every rule runs even after one fails, which is negligible because the checks are cheap.
- Trade-off: the rules must stay independent and side-effect-free for collect-all to be safe, a
  design constraint that is honored.
- Follow-up: a rule that cannot evaluate (a BUY with no quote) returns empty and is left to the other
  rules rather than hard-stopping.

## Pros and cons of the options

### One large method
- Good: a single place.
- Bad: branchy, hard to test, and violates SRP and OCP.

### Chain of Responsibility
- Good: isolated, testable, and extensible rules.
- Bad: a little ceremony per rule.

### Rules engine
- Good: powerful and externally configurable.
- Bad: heavyweight for five rules.

### Fail-fast vs collect-all
- Fail-fast: marginally less work, but an incomplete audit record and a worse client experience.
- Collect-all: a complete record and all reasons at once, at the cost of running every rule.

## Links

- The `ValidationContext` is built once from customer, ledger, and pricing state.
- `ValidationFailed` flows through the outbox for audit ([ADR-0006](../system/0006-transactional-outbox.md)).
- Shares the Open-Closed spirit of the per-source adapters
  ([ADR-0015](../pricing/0015-price-adapter-per-source.md)).
