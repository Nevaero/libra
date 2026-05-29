# 0010: Ledger two-phase booking for T+2

- Status: Accepted
- Date: 2026-05-28
- Deciders: Romain

## Context and problem statement

Physical T+2 settlement ([ADR-0003](0003-physical-forex-t2-uniform.md)) means a trade is agreed at
T+0 and the assets are exchanged at T+2. The ledger must record the obligation at T+0 and the
delivery at T+2, without ever mutating a posting, and it must keep settled funds distinct from
committed-but-unsettled funds. The delay is not arbitrary: the broker does not hold the counter
asset on hand, and over T+2 the funds transit between custody accounts (nostros) and counterparties.

## Decision drivers

- Append-only immutability: a posting is never edited or deleted, and a correction is a new entry.
  This is required for a complete audit trail.
- Record the obligation at T+0 so balances and exposure reflect the in-flight trade.
- Keep the available balance distinct from the book balance, so unsettled funds cannot be spent.
- Model the real in-transit state (nostro and counterparty movement across the T+2 window).
- Use one mechanism for FX and equities.

## Considered options

1. Post a single entry at T+2.
2. Post a single final entry at T+0.
3. Post at T+0 and flip a `pending` flag to `settled` at T+2.
4. Two-phase booking with dedicated pending accounts.

## Decision outcome

Chosen option: **two-phase booking.** A `BOOKING` entry at T+0 posts to dedicated pending accounts,
and a `SETTLEMENT` entry at T+2 transfers pending to final.

Immutability is the non-negotiable that eliminates option 3: flipping a flag mutates a posting and
destroys the audit trail, so "what did this account look like last week" stops being answerable.
Two-phase expresses the whole lifecycle as two append-only entries instead of one mutated entry. It
records the obligation the moment the trade executes, where option 1 would hide it for two days, and
it does not claim a delivery that has not happened, where option 2 would let a client spend
unsettled funds.

It also mirrors reality. The broker does not have the counter asset on hand; across T+2 the funds
move between nostro accounts (the broker's cash held at correspondent banks) and counterparties. The
pending accounts are the ledger's representation of that in-transit position, which makes the
trade's settlement exposure explicit, the window where settlement risk (in the worst case, Herstatt
risk) lives, and which Delivery-versus-Payment exists to contain. The `Balance` projection derives
`availableBalance = bookBalance - pendingDebits + pendingCredits` from the pending-account
positions, so unsettled funds are visible but unspendable, with the same mechanism for every asset
class.

### Consequences

- Good: a fully immutable, auditable trail of both the commitment and the delivery.
- Good: a correct available balance, so unsettled funds cannot be spent.
- Good: settlement exposure is explicit on the pending accounts.
- Trade-off: two entries per trade and a set of pending accounts to maintain, plus the settlement
  step that moves pending to final.
- Trade-off: the projection derives available from pending rather than reading a single column.
- Follow-up: the settlement phase is driven by the T+2 batch
  ([ADR-0019](0019-settlement-batch-failure-isolation.md)), and the booking entry itself is the DvP
  entry ([ADR-0020](0020-trading-dvp-booking.md)).

## Pros and cons of the options

### Post only at T+2
- Good: a single entry.
- Bad: the commitment is invisible for two days, with no T+0 record.

### Post final at T+0
- Good: a single entry.
- Bad: claims a delivery that has not happened and corrupts the available balance.

### Flip a flag
- Good: a single entry with a status field.
- Bad: mutates a posting, which breaks immutability and the audit trail.

### Two-phase booking
- Good: immutable, auditable, real-world accurate, with a correct available balance.
- Bad: two entries and a set of pending accounts.

## Links

- Realizes the T+2 model of [ADR-0003](0003-physical-forex-t2-uniform.md).
- The settlement phase is driven by the batch
  ([ADR-0018](0018-settlement-synchronous-orchestration.md),
  [ADR-0019](0019-settlement-batch-failure-isolation.md)).
- The booking entry is the DvP two-leg entry ([ADR-0020](0020-trading-dvp-booking.md)).
- The available-balance derivation relates to the Balance projection
  ([ADR-0012](../0012-balance-projection-pessimistic-locking.md)).
