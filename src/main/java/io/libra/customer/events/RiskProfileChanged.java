package io.libra.customer.events;

import io.libra.customer.entities.enums.RiskProfile;

import java.time.Instant;
import java.util.UUID;

public record RiskProfileChanged(
        UUID customerId,
        RiskProfile previousProfile,
        RiskProfile newProfile,
        Instant occurredAt
) {
}
