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

Records are grouped by the domain they belong to, mirroring the subfolders. All are Accepted.

### `system/` (cross-cutting)

| ADR | Title |
|---|---|
| [0001](system/0001-modular-monolith.md) | Modular monolith over microservices |
| [0002](system/0002-named-interface-boundaries.md) | Module boundaries via fine-grained named interfaces, verified |
| [0003](system/0003-physical-forex-t2-uniform.md) | Physical Forex with T+2, applied uniformly to equities |
| [0004](system/0004-money-minor-units.md) | Money as `BIGINT` minor units |
| [0005](system/0005-uuidv7-instant-utc.md) | UUIDv7 identifiers and `Instant` UTC timestamps |
| [0006](system/0006-transactional-outbox.md) | Transactional outbox for all events |
| [0007](system/0007-anti-corruption-layer.md) | Anti-corruption layer: domain records vs JPA POJOs |
| [0008](system/0008-reference-resolution-spi.md) | Dependency inversion for reference resolution |
| [0009](system/0009-sync-command-async-fanout.md) | Synchronous command path, asynchronous fan-out |

### `ledger/`

| ADR | Title |
|---|---|
| [0010](ledger/0010-ledger-two-phase-booking.md) | Two-phase booking (pending then final) for T+2 |
| [0011](ledger/0011-double-entry-invariant-at-construction.md) | Double-entry invariant validated at construction |
| [0012](ledger/0012-balance-projection-pessimistic-locking.md) | Pessimistic locking on the Balance projection |

### `core/` (reference)

| ADR | Title |
|---|---|
| [0013](core/0013-security-master-extraction.md) | Security Master extracted from `core` |

### `pricing/`

| ADR | Title |
|---|---|
| [0014](pricing/0014-pricing-optimistic-upsert.md) | Lock-free optimistic upsert on `sequence` |
| [0015](pricing/0015-price-adapter-per-source.md) | One adapter per source, transport vs format |

### `customer/`

| ADR | Title |
|---|---|
| [0016](customer/0016-customer-regulatory-lifecycle.md) | Regulatory lifecycle state machine |

### `validation/`

| ADR | Title |
|---|---|
| [0017](validation/0017-validation-chain-of-responsibility.md) | Chain of Responsibility, collect-all |

### `settlement/`

| ADR | Title |
|---|---|
| [0018](settlement/0018-settlement-synchronous-orchestration.md) | Synchronous orchestration over event-driven |
| [0019](settlement/0019-settlement-batch-failure-isolation.md) | Daily batch with per-instruction isolation |

### `trading/`

| ADR | Title |
|---|---|
| [0020](trading/0020-trading-dvp-booking.md) | Delivery-versus-Payment two-leg booking |
| [0021](trading/0021-trading-execution-simulator-idempotency.md) | In-memory execution simulator and idempotency |
