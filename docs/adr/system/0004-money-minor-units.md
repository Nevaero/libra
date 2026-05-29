# 0004: Money as BIGINT minor units

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Monetary amounts are everywhere in Libra: balances, postings, prices, notionals. We need one
representation that is exact, fast, hard to misuse, and able to cover both currency amounts (cents)
and FX precision (sub-cent pips). Floating point is disqualified for money on sight, so the real
choice is between arbitrary-precision decimals and scaled integers.

## Decision drivers

- Exactness: money arithmetic must be exact and deterministic, with no representation error.
- Avoid primitive obsession and the `BigDecimal` footguns (`equals` vs `compareTo`, silent scale
  drift, nullability).
- Performance and storage: a single 8-byte integer column with fast B-tree indexes.
- Fail fast: overflow and silent rounding should be impossible by construction.
- Cover varying precision per asset (EUR has 2 decimals, JPY has 0, an FX price uses scale 5).

## Considered options

1. `double` / `float`.
2. `BigDecimal` in Java with `NUMERIC` in the database.
3. `BIGINT` minor units wrapped in a `Money` value object, with the decimal point implied by a
   per-asset scale.

## Decision outcome

Chosen option: **`BIGINT` minor units in a `Money` value object, with the point implied by the
asset's scale.**

This is fixed-point arithmetic. We store the integer count of the smallest unit, and the scale
(the currency's decimals, or a quote's `priceScale`) shifts the point only when a value is
displayed or converted. Every operation in between stays pure integer math, which is exact and
fast.

The `Money` value object concentrates the rules instead of scattering `BigDecimal` calls across the
codebase: it rejects cross-asset operations, guards arithmetic with `Math.addExact` so overflow
fails fast, and constructs from a decimal only through `Money.of` with `RoundingMode.UNNECESSARY`,
so a caller can never round silently. A signed 64-bit integer spans about plus or minus 9.2
quintillion (9.2e18). World GDP is on the order of 1e14 dollars, roughly 1e16 in cents, which
leaves four orders of magnitude of headroom. The signedness is deliberate, since the ledger's sign
convention relies on negative values (a client liability account, debit against credit), so a
signed `BIGINT` is exactly what we want. Amounts persist as two columns (`amount BIGINT` plus
`asset_code`) rather than a composite type, which keeps them queryable and index-friendly.

### Consequences

- Good: exact, fast, compact money everywhere, with one `Money` type in every public business
  signature.
- Good: overflow fails fast, rounding is always explicit, and cross-asset operations are rejected
  at the type level.
- Trade-off: a raw minor-unit value cannot be read without knowing the asset's scale. Mitigated by
  never exposing raw `long` in business APIs and by `Money.toDecimal` for display.
- Trade-off: an instrument needing very high precision would require a larger scale, though `long`
  still covers realistic ranges.
- Follow-up: prices carry their own `priceScale` (per currency pair or security), distinct from a
  currency's decimals.

## Pros and cons of the options

### double / float
- Good: fastest, native.
- Bad: inexact for decimal money; representation error accumulates. Disqualified.

### BigDecimal + NUMERIC
- Good: exact and arbitrary precision.
- Bad: heavyweight, slower, nullable, and a steady source of `equals`/`compareTo` and scale bugs.

### BIGINT minor units + Money value object
- Good: exact integer arithmetic, compact storage, fast indexes, rules in one place.
- Bad: requires tracking a per-asset scale and a value object to stay ergonomic.

## Links

- `Money` lives in `core.entities`; persistence is `MoneyEntity` plus `MoneyMapper` (two columns).
- Quote precision uses `priceScale` (see [ADR-0014](../pricing/0014-pricing-optimistic-upsert.md) and the
  pricing model).
- Supports the ledger's double-entry invariant ([ADR-0011](../ledger/0011-double-entry-invariant-at-construction.md)).
