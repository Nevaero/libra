package io.libra.customer.entities.enums;

public enum CustomerStatus {
    PENDING_KYC,   // créé, en attente de vérification documentaire
    ACTIVE,        // KYC validé, peut trader
    SUSPENDED,     // compliance ou volontaire — ne peut pas trader, comptes ledger conservés
    CLOSED         // terminal : ne peut plus rien faire, comptes ledger fermés
}
