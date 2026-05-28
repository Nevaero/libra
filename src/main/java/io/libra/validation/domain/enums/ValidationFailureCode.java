package io.libra.validation.domain.enums;

public enum ValidationFailureCode {
    INSUFFICIENT_FUNDS,         // availableBalance < exposition de l'ordre
    CUSTOMER_NOT_ACTIVE,        // status != ACTIVE (PENDING_KYC, SUSPENDED, CLOSED)
    KYC_INSUFFICIENT,           // kycLevel trop bas pour l'instrument visé
    INSTRUMENT_NOT_TRADABLE,    // SUSPENDED, HALTED, DELISTED (Security) ou DEACTIVATED (CurrencyPair)
    LIMIT_PRICE_OUT_OF_BOUNDS,  // limitPrice irréaliste vs latestQuote (sanity check)
    MARKET_CLOSED               // heures de marché fermées pour l'instrument
}
