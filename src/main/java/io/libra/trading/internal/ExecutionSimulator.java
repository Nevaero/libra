package io.libra.trading.internal;

import io.libra.core.entities.Money;
import io.libra.core.entities.enums.Side;
import io.libra.core.persistence.resolution.InstrumentRefs;
import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.port.PricingService;
import io.libra.trading.domain.Order;
import io.libra.trading.domain.enums.OrderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// Phase-1, in-memory execution venue : fills the full quantity at the current quote (the ask for a
// BUY, the bid for a SELL) read from pricing. A MARKET order always fills if a quote exists ; a
// LIMIT order fills only when the market has crossed its price, otherwise no fill (the order is
// then cancelled by the orchestrator). No partial fills, no slippage — that is phase-2 venue work.
@Component
@RequiredArgsConstructor
public class ExecutionSimulator {

    // The simulated venue we book the contra side against. Informational on the Trade ; the real
    // ledger counterparty accounts are the house FX/MARKET counterparty (resolved at booking).
    private static final UUID SIMULATED_VENUE = UUID.fromString("0190a000-0000-7000-8000-00000000fee1");

    private final PricingService pricingService;

    public Optional<Fill> execute(Order order) {
        UUID instrumentId = InstrumentRefs.idOf(order.instrument());
        Optional<LatestQuote> maybeQuote = pricingService.getLatestQuote(instrumentId, Tenor.SPOT);
        if (maybeQuote.isEmpty()) {
            return Optional.empty();
        }
        LatestQuote quote = maybeQuote.get();

        long fillPrice;
        if (order.side() == Side.BUY) {
            long ask = quote.askMinorUnits();
            if (order.orderType() == OrderType.LIMIT && ask > order.limitPriceMinorUnits()) {
                return Optional.empty();   // limit below the ask — never crossed
            }
            fillPrice = ask;
        } else {
            long bid = quote.bidMinorUnits();
            if (order.orderType() == OrderType.LIMIT && bid < order.limitPriceMinorUnits()) {
                return Optional.empty();   // limit above the bid — never crossed
            }
            fillPrice = bid;
        }

        return Optional.of(new Fill(SIMULATED_VENUE, order.quantity(), fillPrice, quote.priceScale()));
    }

    // A complete fill : the contra venue, the executed base quantity, and the execution price with
    // the scale it was quoted at (the booker needs the scale to compute the quote-leg notional).
    public record Fill(UUID counterpartyId, Money executedQuantity, long executedPriceMinorUnits, int priceScale) {
    }
}
