package io.libra.pricing.client.model.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// Raw JSON object from the OANDA v20 pricing stream, deserialized by Jackson. NOT a domain
// type : the adapter parses the string prices into minor units and maps to PriceTick.
//
// The stream also pushes {"type":"HEARTBEAT","time":...} keep-alives — the adapter ignores
// those. @JsonIgnoreProperties(ignoreUnknown=true) because the real v20 payload carries more
// fields than we consume.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OandaPrice(
        // "PRICE" for a quote, "HEARTBEAT" for a keep-alive — the adapter only acts on "PRICE".
        String type,
        // Instrument in OANDA convention (underscore), e.g. "EUR_USD". Mapped to an instrumentId.
        String instrument,
        // RFC3339 / ISO-8601 UTC timestamp of the quote → PriceTick.quoteTime. OANDA carries NO
        // sequence number, so the adapter derives PriceTick.sequence from this time.
        String time,
        // Bid side, best price first : the prices a seller of the base currency receives.
        List<OandaPriceBucket> bids,
        // Ask/offer side, best price first : the prices a buyer of the base currency pays.
        List<OandaPriceBucket> asks,
        // Worst-case bid OANDA uses to value/close a long position (risk/margin) — not the
        // tradable top-of-book. Kept for completeness; not mapped to PriceTick.
        String closeoutBid,
        // Worst-case ask, symmetric to closeoutBid.
        String closeoutAsk,
        // "tradeable" | "non-tradeable" | "invalid" — instrument availability at this instant.
        String status,
        // Convenience boolean mirroring `status` : whether the instrument is currently tradeable.
        Boolean tradeable
) {
}
