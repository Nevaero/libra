package io.libra.validation.rules;

import io.libra.validation.entities.ValidationContext;
import io.libra.validation.entities.ValidationFailureReason;

import java.util.Optional;

// Vérifie : context.customer().status() == ACTIVE.
// Tout autre statut (PENDING_KYC, SUSPENDED, CLOSED) doit bloquer le trade.
public record CustomerActiveCheckRule() implements ValidationRule {

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        // Phase 1 : logique métier à venir.
        return Optional.empty();
    }
}
