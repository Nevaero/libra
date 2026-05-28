package io.libra.pricing.client.model.fix;

import java.util.List;

// Raw, parsed form of a FIX 4.4 MarketDataSnapshotFullRefresh (MsgType 35=W) — the top-of-book
// a bank/ECN streams over a FIX session. NOT a domain type : the adapter translates it into a
// PriceTick (parses the string prices, maps the symbol to an instrumentId, etc.).
//
// FIX tag numbers in [..]. Prices/sizes stay as String because on the wire FIX is ASCII
// tag=value — turning them into minor units is the adapter's normalization step, not the
// model's job.
public record FixMarketDataSnapshot(
        // [49] SenderCompID — identity of the session that sent the message (the provider/counterparty).
        String senderCompId,
        // [56] TargetCompID — recipient identity (us).
        String targetCompId,
        // [34] MsgSeqNum — session-level sequence number, strictly monotonic per FIX session.
        // This is our ordering key passthrough → PriceTick.sequence.
        long msgSeqNum,
        // [52] SendingTime — UTC timestamp the provider emitted the message (FIX UTCTimestamp,
        // e.g. "20260528-10:00:00.123") → our PriceTick.quoteTime.
        String sendingTime,
        // [262] MDReqID — id of the MarketDataRequest (35=V) this snapshot answers.
        String mdReqId,
        // [55] Symbol — instrument in FIX convention, e.g. "EUR/USD". The adapter resolves it to
        // an instrumentId.
        String symbol,
        // [268] NoMDEntries — repeating group : one entry per side (and per level). For top-of-book
        // FX you get two : a Bid and an Offer.
        List<FixMdEntry> entries
) {
}
