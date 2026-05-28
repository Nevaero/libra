package io.libra.trading.events;

import io.libra.trading.entities.ParentOrder;

import java.time.Instant;

public record ParentOrderSubmitted(ParentOrder parentOrder, Instant occurredAt) {
}
