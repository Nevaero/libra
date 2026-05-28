package io.libra.pricing.client;

import io.libra.pricing.domain.enums.Tenor;

import java.util.UUID;

// Binds one of our instruments to a provider's symbol, with the price scale and tenor to stamp
// on the resulting PriceTick. Registered at subscription time ; incoming messages carry only
// the provider's symbol, so this is how the adapter recovers our instrumentId.
public record Subscription(UUID instrumentId, String symbol, Tenor tenor, int priceScale) {
}
