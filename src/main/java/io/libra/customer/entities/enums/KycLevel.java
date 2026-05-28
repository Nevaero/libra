package io.libra.customer.entities.enums;

// Niveaux de KYC inspirés MIFID II / FINMA (simplifiés pour la démo).
public enum KycLevel {
    NONE,       // pas de KYC effectué — ne peut rien faire de transactionnel
    BASIC,      // identité vérifiée — accès aux instruments simples (cash, FX spot petit ticket)
    ENHANCED,   // due diligence renforcée — accès elargi (equity, FX spot illimité)
    FULL        // due diligence complète — accès à tous les instruments autorisés par la catégorie client
}
