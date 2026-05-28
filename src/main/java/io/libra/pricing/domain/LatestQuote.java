package io.libra.pricing.domain;

import io.libra.pricing.domain.enums.Tenor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Latest quote per (instrument, tenor). Persistence handled by LatestQuoteEntity + LatestQuoteMapper.
//
// Two identities coexist : `id` is a surrogate UUIDv7 primary key (uniform with every other
// table, stable across re-pricings), while (instrumentId, tenor) is the *business* key — the
// tuple callers look a quote up by, and the UNIQUE constraint enforced in the schema.
public record LatestQuote(
        UUID id,
        UUID instrumentId,
        Tenor tenor,
        long bidMinorUnits,
        long askMinorUnits,
        long bidSize,
        long askSize,
        int priceScale,
        Instant quoteTime,
        Instant receivedAt,
        UUID providerId,
        long sequence,
        // Last traded price + size. Nullable : populated for equities, NULL for FX (quote-driven,
        // no central tape). Carried separately from bid/ask because a tick is fundamentally a quote.
        Long lastPriceMinorUnits,
        Long lastSize
) {

    public LatestQuote {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(tenor, "tenor must not be null");
        Objects.requireNonNull(quoteTime, "quoteTime must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        if (priceScale < 0 || priceScale > 8) {
            throw new IllegalArgumentException("priceScale must be in [0, 8], got: " + priceScale);
        }
    }
}
