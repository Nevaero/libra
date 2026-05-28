package io.libra.core.persistence.resolution;

import java.util.UUID;

// Flat reference to an Instrument (Security | CurrencyPair) as stored in persistence :
// (instrumentType, instrumentId). Lookup key for instrument resolution.
public record InstrumentRef(String type, UUID id) {

    public static final String SECURITY = "SECURITY";
    public static final String CURRENCY_PAIR = "CURRENCY_PAIR";
}
