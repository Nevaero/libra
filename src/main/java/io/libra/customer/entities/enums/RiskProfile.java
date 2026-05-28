package io.libra.customer.entities.enums;

// Résultat du suitability test MIFID II — influence ce que validation autorise comme trades.
public enum RiskProfile {
    CONSERVATIVE,   // tolérance faible : cash, FX spot
    BALANCED,       // tolérance moyenne : + equity standard
    AGGRESSIVE      // tolérance élevée : + leveraged, derivés (hors-scope phase 1)
}
