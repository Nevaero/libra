package io.libra.customer.events;

import io.libra.customer.domain.enums.KycLevel;

import java.time.Instant;
import java.util.UUID;

public record KycLevelChanged(
        UUID customerId,
        KycLevel previousLevel,
        KycLevel newLevel,
        Instant occurredAt
) {
}
