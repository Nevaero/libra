package io.libra.validation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Rejected(
        UUID orderId,
        Instant validatedAt,
        List<ValidationFailureReason> reasons
) implements ValidationResult {

    public Rejected {
        if (reasons == null || reasons.isEmpty()) {
            throw new IllegalArgumentException("Rejected result must carry at least one reason");
        }
        reasons = List.copyOf(reasons);
    }
}
