package io.libra.validation.domain;

import java.time.Instant;
import java.util.UUID;

public record Approved(UUID orderId, Instant validatedAt) implements ValidationResult {
}
