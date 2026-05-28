package io.libra.customer.domain.enums;

// Catégorisation MIFID II. Détermine le niveau de protection réglementaire applicable.
public enum ClientCategory {
    RETAIL,                  // protection maximale (clients particuliers)
    PROFESSIONAL,            // protection réduite (sociétés, gérants de fortune)
    ELIGIBLE_COUNTERPARTY    // protection minimale (banques, fonds, autres brokers)
}
