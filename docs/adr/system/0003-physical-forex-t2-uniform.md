# 0003: Physical Forex with T+2, applied uniformly to equities

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

A multi-asset broker can offer FX two ways. Synthetic FX is a cash-settled derivative: the client
holds a position on the price and never receives the currency. Physical FX is real delivery: the
client exchanges one currency for another and takes ownership at a value date, which for spot is
T+2 (two business days later). Equities settle on a delay too. We need to decide what Libra
actually models, and whether FX and equities share one settlement path or each get their own. The
project exists to demonstrate the core-banking mechanics a real broker runs, at a quality that
could plausibly ship.

## Decision drivers

- Demonstrate the substance of core banking: real settlement, value dates, and double-entry
  bookkeeping, the parts that synthetic price tracking hides.
- Align with the differentiator (Swissquote's flagship is physical FX) and stand out from the
  typical CRUD portfolio project.
- Keep a shared core. FX and equities overlap by roughly 80% (ledger, market data, order engine),
  so the asset-specific settlement logic should live in one place rather than be duplicated.
- Production-grade realism: the model should be plausible enough to publish, not a toy.

## Considered options

1. Synthetic FX (cash-settled), equities instant.
2. Physical FX with T+2, equities settle instantly.
3. Physical FX with T+2, equities also T+2 (one uniform settlement model).

## Decision outcome

Chosen option: **physical Forex with T+2 settlement, applied uniformly to equities.**

The unifying insight is that both trades are Delivery-versus-Payment. An FX trade exchanges a
currency leg for a currency leg; an equity trade exchanges a security leg for a cash leg; in both
cases the two legs are delivered against each other at a value date. That lets one settlement
engine serve every asset class: a single `SettlementInstruction`, a two-phase booking (pending at
T+0, final at T+2), and a business-day calculator for value dates.

Synthetic FX would be simpler, yet it would throw away exactly the core-banking mechanics this
project exists to show, and it models a price derivative rather than real ownership. Special-casing
FX while leaving equities instant would split booking into two code paths and forfeit the
shared-core argument. Designing a data model general enough to fit both asset classes took several
iterations, and that difficulty is the point: the result is a core that could ship rather than a
demo.

### Consequences

- Good: one generic settlement engine across asset classes, with the asset-specific complexity
  (value dates, T+2, calendars) isolated in the `settlement` module.
- Good: the double-entry ledger holds real positions and real delivery, the substance of a broker.
- Good: a strong, defensible differentiator in interviews.
- Trade-off: real settlement cycles vary. US equities moved to T+1 in 2024, FX spot is mostly T+2
  with exceptions (USD/CAD is T+1), and crypto is near-instant. Libra standardizes on T+2 uniformly
  as a deliberate simplification to keep a single engine; a per-instrument cycle can be added later
  as a field on the instruction.
- Trade-off: more upfront design effort than synthetic FX (value dates, business-day calendars,
  two-phase booking, dedicated pending accounts).
- Follow-up: a new asset class plugs in by classifying its legs as Delivery-versus-Payment.

## Pros and cons of the options

### Synthetic FX, equities instant
- Good: trivial to build, no settlement domain at all.
- Bad: a price derivative rather than ownership; hides the core-banking mechanics; no
  differentiator.

### Physical FX with T+2, equities instant
- Good: real FX settlement.
- Bad: two booking code paths to maintain, and the shared-core story is lost.

### Physical FX with T+2, equities uniform T+2
- Good: one generic engine, a genuinely shared core, the strongest realism and pitch.
- Bad: the most upfront design effort, and uniform T+2 simplifies the real, varied cycles.

## Links

- Realized by the `settlement` module (two-phase booking, `BusinessDayCalculator`) and the ledger's
  pending and final accounts.
- Relates to [ADR-0010](0010-ledger-two-phase-booking.md) (two-phase booking),
  [ADR-0018](0018-settlement-synchronous-orchestration.md) (synchronous orchestration), and
  [ADR-0020](0020-trading-dvp-booking.md) (DvP booking).
