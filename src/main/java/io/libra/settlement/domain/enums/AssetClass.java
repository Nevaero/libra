package io.libra.settlement.domain.enums;

// Carried on a SettlementInstruction so the batch can publish the right sealed settled event
// (FxTradeSettled vs EquityTradeSettled) without re-reading the trade or the ledger entry.
public enum AssetClass {
    FX,
    EQUITY
}
