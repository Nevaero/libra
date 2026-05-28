package io.libra.ledger.domain.enums.entry;

public enum EntryPhase {
    BOOKING,      // T+0 : postings sur les comptes pending, engagement enregistré
    SETTLEMENT,   // T+2 : postings de transfert pending → comptes finaux
    IMMEDIATE     // T+0 final (deposit/withdrawal cash, dividende crédité, etc.)
}
