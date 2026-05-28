package io.libra.pricing.events.price;

import io.libra.pricing.entities.enums.Tenor;

import java.time.Instant;
import java.util.UUID;

public record PriceTick(UUID id, UUID instrumentId, long bidMinorUnits,
                        long askMinorUnits, long bidSize, long askSize, Instant quoteTime,
                        Instant receivedAt, UUID providerId, Tenor tenor, int priceScale) {
}
