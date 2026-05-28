package io.libra.settlement.domain.enums;

public enum SettlementStatus {
    PENDING,    // créée à T+0, en attente du batch matinal à valueDate
    SETTLED,    // batch traité avec succès, ledger SETTLEMENT entry posée
    FAILED      // batch traité avec échec (counterparty, nostro, etc.), reschedule = nouvelle instruction
}
