# Architecture Decision Records

This directory holds Libra's Architecture Decision Records (ADRs). Each one captures a single
decision: the context, the options weighed, the choice, and its consequences. They explain the
*why* behind the structure drawn in [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md).

- **Format:** [MADR](https://adr.github.io/madr/). New records start from [`template.md`](./template.md).
- **Numbering:** zero-padded, sequential, never reused. The number is permanent once assigned.
- **Immutability:** an accepted ADR is not edited to reverse it. A later ADR supersedes it, and the
  old one is marked `Superseded by ADR-XXXX`.

## Status legend

- **Accepted:** decided and reflected in the code.
- **Planned:** on the backlog, record not written yet (the decision may already live in the code).
- **Superseded:** replaced by a later ADR.

## Index

### Cross-cutting (system level)

| ADR | Title | Status |
|---|---|---|
| [0001](system/0001-modular-monolith.md) | Modular monolith over microservices | Accepted |
| [0002](system/0002-named-interface-boundaries.md) | Module boundaries via fine-grained named interfaces, verified | Accepted |
| [0003](system/0003-physical-forex-t2-uniform.md) | Physical Forex with T+2, applied uniformly to equities | Accepted |
| [0004](system/0004-money-minor-units.md) | Money as `BIGINT` minor units | Accepted |
| [0005](system/0005-uuidv7-instant-utc.md) | UUIDv7 identifiers and `Instant` UTC timestamps | Accepted |
| [0006](system/0006-transactional-outbox.md) | Transactional outbox for all events | Accepted |
| [0007](system/0007-anti-corruption-layer.md) | Anti-corruption layer: domain records vs JPA POJOs | Accepted |
| [0008](system/0008-reference-resolution-spi.md) | Dependency inversion for reference resolution | Accepted |
| [0009](system/0009-sync-command-async-fanout.md) | Synchronous command path, asynchronous fan-out | Accepted |

### Per-module

| ADR | Title | Status |
|---|---|---|
| [0010](ledger/0010-ledger-two-phase-booking.md) | Ledger: two-phase booking (pending then final) for T+2 | Accepted |
| [0011](ledger/0011-double-entry-invariant-at-construction.md) | Ledger: double-entry invariant validated at construction | Accepted |
| [0012](ledger/0012-balance-projection-pessimistic-locking.md) | Ledger: pessimistic locking on the Balance projection | Accepted |
| [0013](core/0013-security-master-extraction.md) | Reference: Security Master extracted from `core` | Accepted |
| [0014](pricing/0014-pricing-optimistic-upsert.md) | Pricing: lock-free optimistic upsert on `sequence` | Accepted |
| [0015](pricing/0015-price-adapter-per-source.md) | Pricing: one adapter per source, transport vs format | Accepted |
| [0016](./0016-customer-regulatory-lifecycle.md) | Customer: regulatory lifecycle state machine | Accepted |
| [0017](./0017-validation-chain-of-responsibility.md) | Validation: Chain of Responsibility, collect-all | Planned |
| [0018](./0018-settlement-synchronous-orchestration.md) | Settlement: synchronous orchestration over event-driven | Planned |
| [0019](./0019-settlement-batch-failure-isolation.md) | Settlement: daily batch with per-instruction isolation | Planned |
| [0020](./0020-trading-dvp-booking.md) | Trading: Delivery-versus-Payment two-leg booking | Planned |
| [0021](./0021-trading-execution-simulator-idempotency.md) | Trading: in-memory execution simulator and idempotency | Planned |
