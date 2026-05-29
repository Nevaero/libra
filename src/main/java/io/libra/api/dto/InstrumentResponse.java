package io.libra.api.dto;

import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Instrument;
import io.libra.core.entities.Security;

import java.util.UUID;

// Flattens the sealed Instrument into one wire shape. `kind` discriminates; the security-only or
// pair-only fields are null for the other kind.
public record InstrumentResponse(
        UUID id,
        String kind,
        String symbol,
        String quoteCurrencyCode,
        String status,
        Integer priceScale,
        String isin,
        String mic
) {

    public static InstrumentResponse from(Instrument instrument) {
        return switch (instrument) {
            case CurrencyPair cp -> new InstrumentResponse(
                    cp.id(), "CURRENCY_PAIR",
                    cp.baseCurrency().code() + "/" + cp.quoteCurrency().code(),
                    cp.quoteCurrency().code(), cp.status().name(), cp.priceScale(), null, null);
            case Security s -> new InstrumentResponse(
                    s.id(), "SECURITY", s.ticker(), s.quoteCurrency().code(), s.status().name(),
                    null, s.isin(), s.mic());
        };
    }
}
