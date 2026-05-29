# 0008: Dependency inversion for reference resolution

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Entities across the codebase reference an `Asset` or `Instrument` by flat columns (`type`, `code`,
`mic` for an asset; `type`, `id` for an instrument). To rehydrate a domain object, those flat refs
must be resolved into full `Currency` / `Security` / `CurrencyPair` instances, and that reference
data lives in the `reference` module (the Security Master). The naive wiring would let `core`,
`ledger`, and `pricing` call `reference`, which points dependencies the wrong way, bloats `core`
into a business-aware fat kernel, and risks cycles. Resolving one ref at a time would also be a
textbook N+1.

## Decision drivers

- Keep `core` a thin shared kernel of types and contracts, with zero business logic or state.
- Point dependencies correctly: consumers and `core` must not depend on `reference`'s internals.
- Eliminate the N+1 in rehydration.
- Keep resolution swappable, so a different reference source could satisfy the same contract.

## Considered options

1. `core` (or each consumer) depends on `reference`.
2. Each consumer resolves one ref at a time against `reference`.
3. Dependency inversion: `core` declares the resolution SPI, `reference` implements it, and
   resolution is batch-first.

## Decision outcome

Chosen option: **dependency inversion. `core` declares the resolution SPI, `reference` implements
it, and the resolver API is batch-first.**

### Why dependency inversion

The Dependency Inversion Principle has two clauses: high-level modules should not depend on
low-level modules, and both should depend on abstractions; abstractions should not depend on
details, and details should depend on abstractions. The abstraction here is the resolution contract
(`AssetResolver`, `InstrumentResolver`, `ReferenceResolution`, with the flat `AssetRef` and
`InstrumentRef` as keys), and it lives in `core`, the stable kernel. The detail is the
database-backed lookup, and it lives in `reference`. Without inversion, a consumer like `ledger`
would depend on `reference`, a high-level consumer reaching down to a low-level detail. With
inversion, `ledger` depends only on the `core` abstraction, and `reference` depends on `core` to
implement it. The compile-time arrow now runs `reference -> core`, the opposite of the runtime call
flow (a consumer calls `resolve()`, and `reference`'s code executes). That reversal of the arrow
against the call direction is the inversion.

### Why this is an SPI

An ordinary API is a contract you call: a module owns it for its consumers, the way `LedgerService`
is called by trading. A Service Provider Interface is a contract you implement: a module declares it
for a provider to fulfil, the way `core` declares `ReferenceResolution` and `reference` provides it.
Both are Java interfaces; the difference is who implements and which way the dependency points. It
is the pattern behind a JDBC `Driver`, an SLF4J binding, and `java.util.ServiceLoader`, except
Spring's dependency injection wires `reference`'s single implementation wherever the `core`
interface is required. In hexagonal terms, the API is a driving (inbound) port and the SPI is a
driven (outbound) port.

### Why batch-first

The resolver API takes a whole collection of refs. A caller gathers every ref an aggregate tree
needs and calls `resolverFor(refs)` once, which runs a single IN-clause query per asset class and
returns a pre-populated resolver, threaded through the MapStruct mappers as a `@Context`. There is
no per-field lookup to call, so the N+1 is impossible by construction, independent of any
`@Cacheable` proxy.

### Consequences

- Good: `core` stays thin, the module graph is acyclic with `core` at the bottom, and resolution is
  swappable behind the SPI.
- Good: rehydration costs one query per asset class; the N+1 is eliminated by the API's shape.
- Trade-off: a small indirection (an interface in `core`, an implementation in `reference`) and the
  discipline of collecting refs before mapping.
- Trade-off: a batch-first resolver is less obvious to a newcomer than a lazy getter.
- Follow-up: an external master-data source could implement the same SPI without touching any
  consumer.

## Pros and cons of the options

### core/consumers depend on reference
- Good: no indirection.
- Bad: wrong dependency direction, a fat business-aware kernel, and cycle risk.

### Resolve one ref at a time
- Good: a simple direct call.
- Bad: couples every module to `reference`, and resolving per field is an N+1.

### Dependency inversion with a batch SPI
- Good: thin `core`, correct direction, no N+1, swappable resolution.
- Bad: an interface and implementation split, plus the batch discipline.

## Links

- SPI in `core.persistence.resolution`; implementation `ReferenceResolutionImpl` in `reference`.
- The resolver is threaded through the mappers as a `@Context` (see
  [ADR-0007](0007-anti-corruption-layer.md)).
- Keeps the `core` and `reference` boundaries of [ADR-0002](0002-named-interface-boundaries.md).
