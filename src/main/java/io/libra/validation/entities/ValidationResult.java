package io.libra.validation.entities;

import java.time.Instant;
import java.util.UUID;

public sealed interface ValidationResult permits Approved, Rejected {

    UUID orderId();

    Instant validatedAt();
}
