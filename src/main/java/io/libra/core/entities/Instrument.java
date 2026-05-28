package io.libra.core.entities;

// Sealed interface du domaine (typage Java + pattern matching côté services).
// NON persistée directement : les entities qui référencent un Instrument stockent
// `String instrumentType` + `UUID instrumentId` (flat) et résolvent l'instance complète
// via un LookupService applicatif (SecurityRepository / CurrencyPairRepository).
public sealed interface Instrument permits Security, CurrencyPair {
    Asset baseAsset();
    Asset quoteAsset();
}
