package io.libra.pricing.client.impl;

import io.libra.pricing.client.AbstractPriceProviderClient;
import io.libra.pricing.client.Subscription;
import io.libra.pricing.client.model.json.OandaPrice;
import io.libra.pricing.client.model.json.OandaPriceBucket;
import io.libra.pricing.events.PriceTick;
import io.libra.pricing.service.QuoteService;
import io.libra.util.Uuids;

import java.time.Instant;
import java.util.UUID;

// Adapter for a provider streaming OANDA v20 JSON over WebSocket. Its transport (a WebSocket
// client, or the simulator) calls onPrice for each message ; HEARTBEATs are ignored. OANDA
// carries NO sequence, so the ordering key is derived from the quote's emission time.
public class OandaPriceProviderClient extends AbstractPriceProviderClient {

    public OandaPriceProviderClient(QuoteService quoteService, UUID providerId) {
        super(quoteService, providerId);
    }

    public void onPrice(OandaPrice price) {
        if (!"PRICE".equals(price.type())) {
            return;   // HEARTBEAT or any non-quote message
        }
        Subscription subscription = subscriptionFor(price.instrument());
        OandaPriceBucket bestBid = price.bids().getFirst();   // best price first
        OandaPriceBucket bestAsk = price.asks().getFirst();
        Instant quoteTime = Instant.parse(price.time());

        emit(new PriceTick(
                Uuids.newId(),
                subscription.instrumentId(),
                toMinorUnits(bestBid.price(), subscription.priceScale()),
                toMinorUnits(bestAsk.price(), subscription.priceScale()),
                bestBid.liquidity(),
                bestAsk.liquidity(),
                quoteTime,
                Instant.now(),
                providerId(),
                subscription.tenor(),
                subscription.priceScale(),
                sequenceFromQuoteTime(quoteTime),   // no provider seq → derived from quoteTime
                null,    // OANDA is FX : no last trade
                null));
    }
}
