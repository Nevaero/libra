package io.libra.pricing.client;

import io.libra.pricing.events.PriceTick;
import io.libra.pricing.service.QuoteService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Shared skeleton for every provider adapter. Subclasses differ only by wire format and
// transport ; everything common — the subscription registry, the normalization helpers, and
// the hand-off to QuoteService — lives here. New same-format provider = new instance with a
// different providerId ; new format = new subclass.
public abstract class AbstractPriceProviderClient implements PriceProviderClient {

    private final QuoteService quoteService;

    private final UUID providerId;

    private final Map<String, Subscription> subscriptionsBySymbol = new ConcurrentHashMap<>();

    protected AbstractPriceProviderClient(QuoteService quoteService, UUID providerId) {
        this.quoteService = quoteService;
        this.providerId = providerId;
    }

    @Override
    public UUID providerId() {
        return providerId;
    }

    @Override
    public void subscribe(Subscription subscription) {
        subscriptionsBySymbol.put(subscription.symbol(), subscription);
    }

    // Resolve the provider's symbol back to our subscription (instrumentId + scale + tenor).
    protected Subscription subscriptionFor(String symbol) {
        Subscription subscription = subscriptionsBySymbol.get(symbol);
        if (subscription == null) {
            throw new IllegalStateException(
                    "No subscription for symbol '" + symbol + "' on provider " + providerId);
        }
        return subscription;
    }

    // Hand a normalized tick to the read-model. Stale / out-of-order ticks are filtered
    // downstream by QuoteService on the sequence — the adapter just emits.
    protected void emit(PriceTick tick) {
        quoteService.ingestTick(tick);
    }

    // Parse a decimal price string into minor units at the instrument's scale. Fail-fast if the
    // provider sends more precision than the scale — a configuration mismatch worth surfacing.
    protected long toMinorUnits(String price, int priceScale) {
        return new BigDecimal(price).movePointRight(priceScale).longValueExact();
    }

    protected long toUnits(String size) {
        return new BigDecimal(size).longValueExact();
    }

    // Ordering key for providers that carry NO sequence : a monotonic value derived from the
    // emission instant (microsecond resolution). Providers that do carry one pass it through.
    protected long sequenceFromQuoteTime(Instant quoteTime) {
        return quoteTime.getEpochSecond() * 1_000_000 + quoteTime.getNano() / 1_000;
    }
}
