package io.libra.validation.domain;

import java.time.Instant;
import java.util.UUID;

public sealed interface ValidationResult permits Approved, Rejected {

    UUID orderId();

    Instant validatedAt();
}
