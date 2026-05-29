# 0015: One adapter per source, transport versus format

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Libra ingests market data from external providers, each speaking a wire format (FIX 4.4, OANDA v20
JSON). We must choose the unit of an adapter and decide how a foreign format reaches the domain.
Three things are easy to conflate and worth separating: the transport (how bytes arrive), the format
(the wire schema), and the source (the actual provider integration, with its endpoint, auth,
symbology, and quirks).

## Decision drivers

- A source has more identity than its format: endpoints, auth, symbology, and quirks differ even
  between two providers that use the same format.
- Keep the domain decoupled from provider formats.
- Make adding or removing a provider cheap and low-risk.
- Reuse shared adapter mechanics without merging providers into one class.

## Considered options

1. One adapter per format (a single FIX client serves every FIX provider).
2. One adapter per source (the provider integration is the unit; format is handled inside).
3. One mega-adapter with format and provider branches.

## Decision outcome

Chosen option: **one adapter per source, normalizing to a canonical `PriceTick`, with config-driven
subscriptions.**

Per source, because the source is the real integration unit. The format is only one of its traits;
auth, endpoints, symbology, and quirks vary per provider, so keying on the source models reality.
Two sources that share a format share code through `AbstractPriceProviderClient` (a Template Method
shape), rather than one format-keyed client juggling several providers' quirks.

Each adapter is an inbound adapter that translates the raw provider model into a normalized
`PriceTick` and calls the `QuoteService` port. This is the anti-corruption layer of
[ADR-0007](../system/0007-anti-corruption-layer.md) applied at the integration edge instead of the
persistence edge: the foreign format is contained inside the adapter, and the rest of the system
only ever sees the canonical `PriceTick`. That is what makes adding or removing a provider trivial,
the blast radius is a single class, and the core has zero knowledge of FIX or OANDA.

Subscriptions are configuration: a YAML of providers and the instruments they cover, resolved at
startup through `reference`, with a fail-fast check on an unlisted instrument.

The SOLID payoff is concrete: Open-Closed (a new provider is a new subclass plus a YAML line, with
no change to `QuoteService` or existing adapters), Single Responsibility (an adapter changes only for
its provider's quirks), Dependency Inversion (adapters depend on the `QuoteService` port rather than
the reverse), and Liskov substitutability behind `PriceProviderClient`.

### Consequences

- Good: the core is fully decoupled from provider formats, and a new feed is a low-risk,
  config-driven addition.
- Good: shared mechanics are reused through the abstract base, with per-source specifics isolated.
- Trade-off: a normalization step per provider, and a canonical `PriceTick` that must stay
  expressive enough for every source.
- Trade-off: one adapter per source means several small adapters.
- Follow-up: the real transport (the mock-feed) and per-source symbology mapping remain.

## Pros and cons of the options

### One adapter per format
- Good: fewer classes.
- Bad: one client juggles multiple providers' auth, symbology, and quirks.

### One adapter per source
- Good: models the real integration, isolates quirks, and makes add or remove trivial.
- Bad: several small adapters to maintain.

### One mega-adapter
- Good: a single place.
- Bad: a branch-heavy maintenance sink.

## Links

- The canonical `PriceTick` feeds the lock-free upsert
  ([ADR-0014](0014-pricing-optimistic-upsert.md)).
- Config-driven resolution uses the reference SPI ([ADR-0008](../system/0008-reference-resolution-spi.md)).
