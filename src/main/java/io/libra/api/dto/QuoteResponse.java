package io.libra.api.dto;

import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.domain.enums.Tenor;

import java.time.Instant;
import java.util.UUID;

public record QuoteResponse(
        UUID instrumentId,
        Tenor tenor,
        long bidMinorUnits,
        long askMinorUnits,
        long bidSize,
        long askSize,
        int priceScale,
        Instant quoteTime,
        Long lastPriceMinorUnits,
        Long lastSize
) {

    public static QuoteResponse from(LatestQuote q) {
        return new QuoteResponse(
                q.instrumentId(), q.tenor(), q.bidMinorUnits(), q.askMinorUnits(), q.bidSize(),
                q.askSize(), q.priceScale(), q.quoteTime(), q.lastPriceMinorUnits(), q.lastSize());
    }
}
