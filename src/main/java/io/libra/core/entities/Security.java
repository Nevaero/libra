package io.libra.core.entities;

import io.libra.core.entities.enums.SecurityStatus;
import io.libra.core.entities.enums.SecurityType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Domain record. Implements both Asset (when held in a Balance/Money) and Instrument (when traded).
// `quoteCurrency` is the Currency in which prices are quoted, embedded as a domain reference.
// Persistence stores `quote_currency_code` flat and the mapper rehydrates via CurrencyRepository.
public record Security(
        UUID id,
        String isin,
        String ticker,
        String mic,
        Currency quoteCurrency,
        String name,
        SecurityType type,
        SecurityStatus status,
        Instant listedAt,
        Instant delistedAt
) implements Asset, Instrument {

    public Security {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(isin, "isin must not be null");
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(mic, "mic must not be null");
        Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(listedAt, "listedAt must not be null");
    }

    @Override
    public String code() {
        return ticker;
    }

    @Override
    public int decimals() {
        // Securities are integral units (no fractional shares in Libra phase 1).
        return 0;
    }

    @Override
    public Asset baseAsset() {
        return this;
    }

    @Override
    public Asset quoteAsset() {
        return quoteCurrency;
    }
}
