package io.libra.pricing.events;

import io.libra.pricing.domain.enums.Tenor;

import java.time.Instant;
import java.util.UUID;

// Inbound, normalized tick : the value an adapter (PriceProviderClient) produces from its
// provider's raw format and hands to QuoteService.ingestTick. NOT a published event — the
// outbound event is QuoteAdvanced.
//
// `sequence` is the ordering key, normalized by the adapter : the provider's own sequence
// when it has one, otherwise derived from quoteTime. QuoteService compares only this field.
public record PriceTick(UUID id, UUID instrumentId, long bidMinorUnits, long askMinorUnits,
                        long bidSize, long askSize, Instant quoteTime, Instant receivedAt,
                        UUID providerId, Tenor tenor, int priceScale, long sequence,
                        // Last trade carried alongside the quote — nullable (equities ; NULL for FX).
                        Long lastPriceMinorUnits, Long lastSize) {
}
