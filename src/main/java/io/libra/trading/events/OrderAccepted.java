package io.libra.trading.events;

import io.libra.trading.domain.Order;

import java.time.Instant;

public record OrderAccepted(Order order, Instant occurredAt) {
}
