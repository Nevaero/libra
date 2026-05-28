package io.libra.core.entities;

import io.libra.core.entities.enums.CurrencyPairStatus;

import java.util.Objects;
import java.util.UUID;

// Domain record. Persistence handled by CurrencyPairEntity + CurrencyPairMapper.
public record CurrencyPair(
        UUID id,
        Currency baseCurrency,
        Currency quoteCurrency,
        CurrencyPairStatus status,
        int priceScale
) implements Instrument {

    public CurrencyPair {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (baseCurrency.equals(quoteCurrency)) {
            throw new IllegalArgumentException("baseCurrency and quoteCurrency must differ");
        }
        if (priceScale < 0 || priceScale > 8) {
            throw new IllegalArgumentException("priceScale must be in [0, 8], got: " + priceScale);
        }
    }

    @Override
    public Asset baseAsset() {
        return baseCurrency;
    }

    @Override
    public Asset quoteAsset() {
        return quoteCurrency;
    }
}
