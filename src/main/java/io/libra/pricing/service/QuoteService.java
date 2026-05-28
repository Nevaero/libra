package io.libra.pricing.service;

import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.events.PriceTick;

import java.util.Optional;
import java.util.UUID;

// Owns the LatestQuote read-model : ingests normalized ticks (write side) and serves the
// current quote (read side). Adapters call ingestTick ; other modules call getLatestQuote.
public interface QuoteService {

    // Applies a tick to the LatestQuote projection iff it is newer (by sequence), publishing
    // QuoteAdvanced only when the canonical quote actually moves. Idempotent on replays.
    void ingestTick(PriceTick tick);

    Optional<LatestQuote> getLatestQuote(UUID instrumentId, Tenor tenor);
}
