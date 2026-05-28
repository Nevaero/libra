package io.libra.pricing.events;

import io.libra.pricing.domain.enums.Tenor;

import java.time.Instant;
import java.util.UUID;

// Outbound domain event, published via the transactional outbox when an ingested tick
// actually advances the canonical LatestQuote for (instrumentId, tenor). Deduplicated and
// ordered : stale / out-of-order / replayed ticks no-op the upsert and emit nothing, so this
// stream is clean for live consumers (validation pre-trade checks, trading market pricing, UI).
public record QuoteAdvanced(UUID instrumentId, Tenor tenor, long bidMinorUnits, long askMinorUnits,
                            long bidSize, long askSize, int priceScale, Instant quoteTime, long sequence,
                            Long lastPriceMinorUnits, Long lastSize) {
}
