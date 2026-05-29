# 0001: Modular monolith over microservices

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Libra has seven business modules (reference, ledger, pricing, customer, validation, settlement,
trading) plus shared `core` and `util`. We need a structure and a deployment model that enforce
clear domain boundaries from the start and keep our future options open as load or scope grows.
Libra is a solo portfolio project meant to demonstrate architectural discipline, so the choice
also has to hold up under interview scrutiny.

## Decision drivers

- Enforce Domain-Driven Design boundaries explicitly, so a module cannot quietly reach into
  another's internals and erode the design over time.
- Keep the consistency-critical path (the double-entry ledger and the T+2 booking) inside a single
  ACID transaction. Splitting it across services would force distributed transactions or sagas.
- Avoid the operational cost of a distributed system (network failure modes, partial failure,
  deployment and observability overhead) for a single developer with no ops team.
- Preserve the option to extract a module into its own service later, for example if one module
  comes to dominate CPU or memory and needs independent horizontal scaling.

## Considered options

1. Traditional layered monolith, one process with no enforced internal boundaries.
2. Modular monolith, one process with module boundaries enforced by Spring Modulith.
3. Microservices, one deployable service per bounded context.

## Decision outcome

Chosen option: **modular monolith, enforced with Spring Modulith.**

The boundaries are drawn and verified at build time (named interfaces plus
`ApplicationModules.verify()`), which gives the DDD discipline we want without paying the
distributed-systems tax. Microservices most often address an organizational problem (team
autonomy, independent deploy cadence, an instance of Conway's law) rather than a technical one,
and they introduce real complexity and new failure modes. For a single-process, consistency-heavy
broker they would be overkill, and worse for the core, since the ledger and booking flow benefits
from staying in one ACID transaction instead of a saga.

The modular monolith also keeps the future open. Because the boundaries are explicit and enforced,
promoting a hot module to its own service later is a contained refactor. A traditional monolith
promises the same flexibility but rarely delivers it, since without enforcement the boundaries rot
into a big ball of mud and the extraction becomes archaeology. We pay a small price now (drawing
and verifying boundaries) to keep that option genuinely available, to be exercised only at the
last responsible moment.

### Consequences

- Good: bounded contexts are enforced rather than merely suggested. A boundary breach fails the
  build (`ModularityTests`).
- Good: the consistency-critical flows stay in a single transaction, with no saga machinery and no
  eventual-consistency reasoning on the command path.
- Good: optionality is preserved. A module can be promoted to a service on the evolutionary
  ("monolith first") path, and the existing boundaries make that cheap.
- Trade-off: one deployable means no per-module scaling or independent deploy cadence today.
- Trade-off: the application shares a runtime and a database, so a crash or a heavy migration
  affects everything.
- Follow-up: extraction stays a deliberate future option, taken only if a module's load profile
  demands it.

## Pros and cons of the options

### Traditional layered monolith
- Good: simplest to build and operate.
- Bad: nothing stops a module reaching into another's internals; boundaries erode, and a future
  extraction becomes archaeology.

### Modular monolith
- Good: enforced boundaries, a single ACID transaction for the core, one simple deployment, and a
  clean extraction path.
- Bad: no independent scaling or deployment yet; shared runtime and database.

### Microservices
- Good: independent scaling and deployment, team autonomy, fault isolation per service.
- Bad: distributed transactions or sagas for the ledger flow, network failure modes, and heavier
  operations (service discovery, tracing, multi-service deployment). Overkill for a solo project.

## Links

- Realized by the named-interface boundaries and build-time verification in
  [ADR-0002](0002-named-interface-boundaries.md).
- The synchronous, single-transaction command path is covered in
  [ADR-0009](0009-sync-command-async-fanout.md).
- Visualized in [`docs/ARCHITECTURE.md`](../../ARCHITECTURE.md) (Level 2 Containers, Level 3 Components).
