# 0013: Extract the Security Master from core

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Every trading system has a Security Master: the authoritative registry of tradable instruments and
their reference data (ISIN, MIC, ticker, quote currency, status), plus the instrument lifecycle (an
instrument is listed and active, can be suspended or halted, and is eventually delisted). It is a
bounded context with its own persistence and rules. Originally this referential lived in `core`. We
must decide whether it belongs there, given that `core` is the most depended-upon package in the
system.

## Decision drivers

- Keep `core` the most stable and most abstract kernel: pure types and contracts, nothing that
  changes for business reasons.
- Avoid a fat shared kernel, where a heavily depended-upon package also holds volatile state and
  logic and becomes a coupling bottleneck.
- Treat the Security Master as a bounded context with its own persistence, rules, and lifecycle.
- Keep a single source of truth for instrument reference data.

## Considered options

1. Keep the referential in `core`.
2. Duplicate the reference data in each consuming module.
3. Extract a dedicated `reference` module; `core` keeps only the types.

## Decision outcome

Chosen option: **extract a `reference` module. `core` keeps only the types.**

Lead with the fat-kernel risk, because it is the real driver. By the Stable Dependencies Principle,
the most depended-upon package must also be the most stable, which means the most abstract. `core`
sits at the bottom of the dependency graph, so it has to be pure types and contracts that rarely
change. The instrument referential is the opposite: stateful data and lifecycle rules that change
for business reasons, such as a new listing or a suspension. Leaving it in `core` would make `core`
simultaneously the most depended-upon and the most volatile package, which is the fat shared kernel
failure mode, where every reference-data change shakes the entire dependency graph and every module
is coupled to instrument-management concerns it does not care about.

The fix is separation of concerns applied one level deeper than module boundaries. What looked like
one shared thing is two concerns. The vocabulary, namely what an instrument is (the `Currency`,
`Security`, and `CurrencyPair` types), is universal and stable, so it stays in `core`. The master
data and behavior (the registry, its persistence, its lifecycle state machine) is volatile and
belongs to one bounded context, so it moves to `reference`. Even the shared layer is split along its
concerns.

Consumers depend on the stable types and resolve instances through the `core` resolution SPI
([ADR-0008](0008-reference-resolution-spi.md)), so they never touch `reference`'s internals, and
`reference` is free to change its persistence and lifecycle behind that boundary.

### Consequences

- Good: `core` stays thin, stable, and safe to depend on; reference-data churn is contained in one
  module.
- Good: the Security Master is an autonomous bounded context with a single source of truth.
- Good: a clean dependency direction, so a `reference` change does not ripple to consumers.
- Trade-off: one more module, and the indirection of the SPI to obtain an instance.
- Follow-up: an external master-data feed would live behind `reference`, never in `core`.

## Pros and cons of the options

### Keep in core
- Good: no new module.
- Bad: `core` becomes a fat kernel, both volatile and a coupling bottleneck.

### Duplicate per consumer
- Good: no new module.
- Bad: no single source of truth; instruments drift out of sync.

### Extract a reference module
- Good: a thin stable `core` and an autonomous Security Master.
- Bad: an extra module and the SPI indirection to fetch instances.

## Links

- The resolution SPI that keeps consumers off `reference` internals
  ([ADR-0008](0008-reference-resolution-spi.md)).
- Module boundaries and the thin kernel ([ADR-0002](0002-named-interface-boundaries.md)).
