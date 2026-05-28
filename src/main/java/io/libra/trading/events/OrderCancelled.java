package io.libra.trading.events;

import io.libra.trading.domain.Order;

import java.time.Instant;

// Published when an accepted order cannot be filled (no marketable price : no quote, or a LIMIT
// order that the market never crossed). Distinct from OrderRejected, which is a pre-trade refusal.
public record OrderCancelled(Order order, String reason, Instant occurredAt) {
}
