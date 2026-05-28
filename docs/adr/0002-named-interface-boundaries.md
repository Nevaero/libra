# 0002: Module boundaries via fine-grained named interfaces

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

[ADR-0001](0001-modular-monolith.md) chose a modular monolith whose boundaries must be *enforced*,
not merely drawn. Spring Modulith encapsulates by default: a module exposes only its base package,
and every sub-package is internal. Libra's public types live in sub-packages (`port`, `domain`,
`commands`), so we need a deliberate way to publish an API surface, a choice of how finely to slice
it, and a mechanism that keeps the rule from eroding over time.

## Decision drivers

- A boundary rule is only real if it is enforced upstream, at build time, rather than left to a
  reviewer's vigilance.
- Least privilege: a consumer should see only the slice of a module it actually uses.
- The public types span the `port`, `domain`, and `commands` sub-packages, while `persistence`,
  `repository`, `internal`, and `port.impl` must stay hidden.
- The rule's cost is acceptable. It is slightly verbose and will trip up a newcomer, and we take
  that on purpose.

## Considered options

Exposure:

1. OPEN the module (expose every package).
2. Flatten the public types into the base package.
3. Named interfaces over chosen packages.

Granularity, given option 3:

- 3a. One bundled `api` named interface per module.
- 3b. Fine-grained interfaces per role: `port`, `domain`, `commands`.

Enforcement:

- A. `ApplicationModules.verify()` in a `ModularityTests` build test.
- B. Convention and code review only.

## Decision outcome

Chosen: **named interfaces, sliced fine-grained per role (`port`, `domain`, `commands`), enforced
by `ApplicationModules.verify()` in `ModularityTests`.**

- **Exposure.** OPEN would throw away the encapsulation ADR-0001 paid for, and flattening would
  destroy the package structure. Named interfaces publish exactly the packages we choose and keep
  the rest unreachable.
- **Granularity.** Fine-grained applies the Interface Segregation Principle at the module boundary.
  A consumer declares the precise interfaces it needs (for example `validation` depends on
  `ledger :: port` and `ledger :: domain`, and never on `ledger :: commands`). It is more verbose
  than a bundled `api`, and a method that returns a domain type still drags `domain` in, yet the
  explicitness is the discipline we want: an accidental reach shows up in the dependency
  declaration and in the build.
- **Enforcement.** `verify()` is an architecture fitness function. It fails the build on a boundary
  breach, the belt-and-braces defense that stops the first broken window from appearing. The
  friction it adds for a newcomer is the point: the rule is enforced at the root rather than
  discovered in review once the debt is already in.

### Consequences

- Good: boundaries are explicit and least-privilege; a breach fails the build, not a code review.
- Good: internals (`persistence`, `repository`, `internal`, `port.impl`) are unreachable across
  modules.
- Trade-off: `allowedDependencies` lists grow verbose (trading names ten interfaces). Accepted as
  the price of discipline.
- Trade-off: a learning curve for anyone new to Spring Modulith named interfaces.
- Follow-up: every new module publishes `port` / `domain` / `commands` interfaces and earns a
  verify-backed entry. The module Definition of Done in `CLAUDE.md` reflects this.

## Pros and cons of the options

### Bundled `api` interface
- Good: one entry per dependency, low verbosity.
- Bad: coarse. A consumer that needs only the port also sees `domain` and `commands`.

### Fine-grained `port` / `domain` / `commands`
- Good: least privilege, explicit, accidental reaches are visible.
- Bad: verbose dependency lists.

### `verify()` vs review
- Good: `verify()` catches breaches mechanically and on the first build.
- Bad: review is human and easy to skip, so the boundary rots quietly.

## Links

- Enforces the boundaries chosen in [ADR-0001](0001-modular-monolith.md).
- The named interfaces and the full dependency table are in
  [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) (Level 3).
- Implemented in each module's `package-info.java` and in `src/test/java/io/libra/ModularityTests.java`.
