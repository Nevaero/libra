# 0020: Trading Delivery-versus-Payment booking

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

When a trade executes, trading must book it into the ledger. A trade moves two assets: an FX trade
exchanges two currencies, an equity trade exchanges a security for cash. We must decide how trading
expresses the booking so that the ledger's per-asset invariant holds and the T+2 model is honored.

## Decision drivers

- A trade is two-sided (one asset delivered against another), so the booking must move two assets.
- The per-asset double-entry invariant must hold
  ([ADR-0011](0011-double-entry-invariant-at-construction.md)).
- The booking must post to pending accounts for the T+2 model
  ([ADR-0010](0010-ledger-two-phase-booking.md)).
- One booking shape should serve FX and equity
  ([ADR-0003](0003-physical-forex-t2-uniform.md)).

## Considered options

1. A single cash-movement posting per trade.
2. A two-leg Delivery-versus-Payment journal entry.

## Decision outcome

Chosen option: **a single journal entry with two balanced legs, on pending accounts, in DvP form.**

A trade is booked as one journal entry with two legs: the base or security leg (the instrument's
base asset) and the quote or cash leg (the notional, which is quantity times price). Each leg is
balanced per asset, a client posting against the house-counterparty posting, so the entry satisfies
the double-entry invariant per asset by construction.

This is Delivery-versus-Payment, the shape that unifies FX and equity
([ADR-0003](0003-physical-forex-t2-uniform.md)): an FX trade is currency against currency, an equity
trade is security against cash, both expressed as two balanced legs. The sign convention is
ledger-centric, since the client account is a liability for Libra, so CREDIT increases what the
client owns. A BUY credits the client's base leg and debits its quote leg, a SELL flips both, and the
counterparty postings mirror the client's.

The contra side is the house counterparty (`FX_COUNTERPARTY` for currencies, `MARKET_COUNTERPARTY`
for securities), a fixed system owner. Trading resolves-or-opens the four pending accounts it posts
on, plus their final mirrors so the T+2 settlement can transfer pending to final, through the
idempotent `resolveClientAccount` and `resolveCounterpartyAccount` on the ledger port. The booking
entry's `caused_by` is null, since that column is reserved for the settlement-to-booking link, and
the order and trade linkage is carried in the description.

### Consequences

- Good: one booking shape for every asset class, the invariant holds by construction, and the T+2
  pending model is honored.
- Good: trading expresses trade economics while the ledger owns the accounts and the double-entry
  mechanics.
- Trade-off: four accounts to provision per trade, mitigated by idempotent resolve-or-open.
- Trade-off: trading computes the quote-leg notional (quantity times price at the right scale).
- Follow-up: fees and an FX spread would add postings to the same DvP entry.

## Pros and cons of the options

### Single cash-movement posting
- Good: simpler.
- Bad: models a single cash movement, missing the two-asset DvP truth and the physical-FX model.

### Two-leg DvP entry
- Good: real delivery, invariant-balanced, uniform across asset classes.
- Bad: four accounts and a notional calculation per trade.

## Links

- Realizes DvP from [ADR-0003](0003-physical-forex-t2-uniform.md) and books the two-phase model of
  [ADR-0010](0010-ledger-two-phase-booking.md).
- The entry satisfies the invariant of
  [ADR-0011](0011-double-entry-invariant-at-construction.md).
- Settled at T+2 by [ADR-0019](0019-settlement-batch-failure-isolation.md).
