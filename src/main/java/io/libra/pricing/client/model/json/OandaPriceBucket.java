package io.libra.pricing.client.model.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// One price level ("bucket") in the OANDA order book : a price and the depth available at it.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OandaPriceBucket(
        // Raw decimal string, e.g. "1.08523" — parsed to minor units by the adapter using the
        // instrument's price scale.
        String price,
        // Units of the base currency available at this price (OANDA sends an integer).
        long liquidity
) {
}
