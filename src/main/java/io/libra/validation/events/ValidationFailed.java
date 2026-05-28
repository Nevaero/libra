package io.libra.validation.events;

import io.libra.validation.domain.ValidationFailureReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Publié à chaque rejet de validation, indépendamment de l'event `OrderRejected` côté trading.
// Justification MIFID II : traçabilité des décisions de validation pour audit régulateur,
// y compris quand le rejet n'aboutit pas à un Order persisté (validation pré-création).
public record ValidationFailed(
        UUID orderId,
        UUID clientId,
        List<ValidationFailureReason> reasons,
        Instant occurredAt
) {

    public ValidationFailed {
        if (reasons == null || reasons.isEmpty()) {
            throw new IllegalArgumentException("ValidationFailed must carry at least one reason");
        }
        reasons = List.copyOf(reasons);
    }
}
