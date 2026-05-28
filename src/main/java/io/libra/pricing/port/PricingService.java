package io.libra.pricing.port;

import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.domain.enums.Tenor;

import java.util.Optional;
import java.util.UUID;

// Module port for pricing : the single read surface other modules (validation, trading, api)
// use to obtain the current price of an instrument. Tick ingestion stays internal (adapters →
// QuoteService) ; this façade exposes only the synchronous quote read.
public interface PricingService {

    Optional<LatestQuote> getLatestQuote(UUID instrumentId, Tenor tenor);
}
