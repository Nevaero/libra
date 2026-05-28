package io.libra.pricing.client.model.fix;

// One entry of the FIX NoMDEntries [268] repeating group : a single price level on one side.
// Shared by the full snapshot (35=W) and the incremental refresh (35=X).
public record FixMdEntry(
        // [269] MDEntryType — '0' = Bid, '1' = Offer/Ask. (Other values like '2'=Trade exist but
        // are irrelevant to a top-of-book quote and ignored by the adapter.)
        char mdEntryType,
        // [270] MDEntryPx — the price at this level, raw decimal string e.g. "1.08523".
        String mdEntryPx,
        // [271] MDEntrySize — quantity / notional available at this level, raw string.
        String mdEntrySize,
        // [279] MDUpdateAction — only present in an Incremental Refresh (35=X) : '0'=New, '1'=Change,
        // '2'=Delete. Null in a full snapshot (35=W), where every entry is implicitly current.
        Character mdUpdateAction
) {
}
