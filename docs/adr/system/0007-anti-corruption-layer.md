# 0007: Anti-corruption layer between domain and persistence

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

How should the domain model relate to JPA persistence? We want a pure domain (Java records,
immutability, sealed types, invariants enforced in compact constructors) and the maturity of JPA.
These pull in opposite directions, because a JPA entity needs a no-arg constructor, non-final
mutable fields, and brings lazy proxies and dirty checking, none of which a record allows. This is
a trilemma: a pure domain, no mapping boilerplate, and full JPA power, pick two.

## Decision drivers

- Keep the domain pure and free of persistence concerns, so records and constructor-time invariants
  are possible.
- Single Responsibility: a domain type models business rules, a persistence type models the table
  mapping, and each should change for one reason only.
- Use JPA's maturity without letting it dictate the shape of the domain.
- Keep the cost of any mapping layer low and safe.

## Considered options

1. Annotate the domain objects with `@Entity` (the domain is the JPA model).
2. Pure domain records, separate JPA POJOs, and a MapStruct mapper between them.
3. Spring Data JDBC, which maps immutable aggregates directly.

## Decision outcome

Chosen option: **pure domain records in `domain/`, Lombok `@Data @Entity` POJOs in `persistence/`,
and MapStruct between them.**

This is an anti-corruption layer: persistence is an adapter, and ORM concerns (proxies, dirty
checking, mutability, the no-arg constructor) never reach the domain. The domain stays records with
invariants in compact constructors, which is exactly what lets `JournalEntry` validate the
double-entry rule at construction time.

It gives Single Responsibility and decoupling: the domain type owns the business rules, the entity
owns the table mapping, the two evolve independently, and the domain depends on nothing from
`jakarta.persistence`, so it is unit-testable with no database.

The layer has a real cost, paid by MapStruct, an industry-standard compile-time mapper. It
generates plain code with no runtime reflection, and it surfaces mapping mistakes at compile time
(an unmapped property is a warning, observed in practice during development). That removes the
mental load of a hand-written mapper and keeps the translation fast.

### Consequences

- Good: a pure, testable domain; persistence is swappable behind the mapper; invariants are
  enforceable in constructors.
- Good: mapping is compile-time-checked, with no reflection cost at runtime.
- Trade-off: an extra type and mapper per aggregate, accepted because MapStruct generates the code.
- Trade-off: two representations to keep in sync, which the mapper makes explicit and checked.
- Follow-up: the batch resolution that kills the N+1 rides on this mapper layer through a
  MapStruct `@Context` (see [ADR-0008](0008-reference-resolution-spi.md)).

## Pros and cons of the options

### Domain objects are @Entity
- Good: no mapping layer at all.
- Bad: kills records, immutability, and constructor invariants, and leaks JPA proxies and
  mutability into the domain.

### Records + POJOs + MapStruct
- Good: a pure domain, Single Responsibility, and compile-time-checked mapping.
- Bad: an extra type and mapper per aggregate.

### Spring Data JDBC
- Good: maps immutables directly, less boilerplate than option 2.
- Bad: gives up JPA's lazy graphs and ecosystem, and changes the persistence technology.

## Links

- Domain records live in `*/domain`; entities in `*/persistence/entity`; mappers in
  `*/persistence/mapper`.
- Enables constructor-time invariants such as the double-entry check
  ([ADR-0011](0011-double-entry-invariant-at-construction.md)).
- The batch resolution `@Context` threads through these mappers
  ([ADR-0008](0008-reference-resolution-spi.md)).
