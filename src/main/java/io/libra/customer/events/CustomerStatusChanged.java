package io.libra.customer.events;

import io.libra.customer.domain.enums.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerStatusChanged(
        UUID customerId,
        CustomerStatus previousStatus,
        CustomerStatus newStatus,
        String reason,
        Instant occurredAt
) {
}
