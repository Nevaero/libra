package io.libra.core.entities;

// Sealed interface du domaine (typage Java + pattern matching côté services).
// NON persistée directement : les entities qui référencent un Asset stockent
// `String assetType` + `String assetCode` (flat) et résolvent l'instance complète
// via un LookupService applicatif (CurrencyRepository / SecurityRepository).
public sealed interface Asset permits Currency, Security {
    String code();
    String name();
    int decimals();
}
