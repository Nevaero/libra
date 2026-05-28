package io.libra.pricing.client;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

// Holds the live PriceProviderClient instances keyed by provider code. Populated by the
// subscription bootstrap ; consumed by the transports / simulator to route a raw message to the
// adapter of the source that produced it.
public class PriceProviderClientRegistry {

    private final Map<String, PriceProviderClient> byCode;

    public PriceProviderClientRegistry(Map<String, PriceProviderClient> byCode) {
        this.byCode = Map.copyOf(byCode);
    }

    public Optional<PriceProviderClient> find(String providerCode) {
        return Optional.ofNullable(byCode.get(providerCode));
    }

    public PriceProviderClient get(String providerCode) {
        return find(providerCode).orElseThrow(() ->
                new IllegalArgumentException("No price provider client for code: " + providerCode));
    }

    public Collection<PriceProviderClient> all() {
        return byCode.values();
    }
}
