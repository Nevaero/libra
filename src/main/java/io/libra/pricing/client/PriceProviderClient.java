package io.libra.pricing.client;

import java.util.UUID;

// Inbound adapter (driving adapter) for one price source. Implementations own a provider's
// transport (FIX session, WebSocket, polling…) and its wire format ; they convert each raw
// price into a PriceTick and hand it to QuoteService. The format-specific intake method (e.g.
// onSnapshot / onPrice) lives on the concrete client, called by its transport.
public interface PriceProviderClient {

    UUID providerId();

    // Register the mapping between one of our instruments and the symbol this provider uses
    // for it (plus price scale and tenor), so incoming messages — which carry only the
    // provider's symbol — resolve back to our instrumentId.
    void subscribe(Subscription subscription);
}
