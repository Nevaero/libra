package io.libra.trading.events;

import io.libra.trading.entities.Order;

import java.time.Instant;

public record OrderSubmitted(Order order, Instant occurredAt) {
}
