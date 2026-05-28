package io.libra.pricing.client.impl;

import io.libra.pricing.client.AbstractPriceProviderClient;
import io.libra.pricing.client.Subscription;
import io.libra.pricing.client.model.fix.FixMarketDataSnapshot;
import io.libra.pricing.client.model.fix.FixMdEntry;
import io.libra.pricing.events.PriceTick;
import io.libra.pricing.service.QuoteService;
import io.libra.util.Uuids;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

// Adapter for a provider streaming FIX 4.4 market data. Its transport (a QuickFIX/J session, or
// the simulator) calls onSnapshot for each MarketDataSnapshotFullRefresh (35=W). The FIX
// session's MsgSeqNum is already a monotonic ordering key → passed through as PriceTick.sequence.
public class FixPriceProviderClient extends AbstractPriceProviderClient {

    private static final DateTimeFormatter FIX_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss[.SSS]");

    public FixPriceProviderClient(QuoteService quoteService, UUID providerId) {
        super(quoteService, providerId);
    }

    public void onSnapshot(FixMarketDataSnapshot snapshot) {
        Subscription subscription = subscriptionFor(snapshot.symbol());
        FixMdEntry bid = entry(snapshot, '0');
        FixMdEntry ask = entry(snapshot, '1');
        // Last trade (MDEntryType '2') is optional — present for equities, absent for FX.
        FixMdEntry trade = entryOrNull(snapshot, '2');
        Long lastPrice = trade == null ? null : toMinorUnits(trade.mdEntryPx(), subscription.priceScale());
        Long lastSize = trade == null ? null : toUnits(trade.mdEntrySize());

        emit(new PriceTick(
                Uuids.newId(),
                subscription.instrumentId(),
                toMinorUnits(bid.mdEntryPx(), subscription.priceScale()),
                toMinorUnits(ask.mdEntryPx(), subscription.priceScale()),
                toUnits(bid.mdEntrySize()),
                toUnits(ask.mdEntrySize()),
                LocalDateTime.parse(snapshot.sendingTime(), FIX_TIMESTAMP).toInstant(ZoneOffset.UTC),
                Instant.now(),
                providerId(),
                subscription.tenor(),
                subscription.priceScale(),
                snapshot.msgSeqNum(),   // FIX carries its own sequence → passthrough
                lastPrice,
                lastSize));
    }

    private FixMdEntry entry(FixMarketDataSnapshot snapshot, char type) {
        return snapshot.entries().stream()
                .filter(e -> e.mdEntryType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No MDEntry of type '" + type + "' in snapshot for " + snapshot.symbol()));
    }

    private FixMdEntry entryOrNull(FixMarketDataSnapshot snapshot, char type) {
        return snapshot.entries().stream()
                .filter(e -> e.mdEntryType() == type)
                .findFirst()
                .orElse(null);
    }
}
