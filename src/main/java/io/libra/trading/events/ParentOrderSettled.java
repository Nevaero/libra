package io.libra.trading.events;

import io.libra.trading.domain.ParentOrder;

import java.time.Instant;

public record ParentOrderSettled(ParentOrder parentOrder, Instant occurredAt) {
}
