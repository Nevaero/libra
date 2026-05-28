package io.libra.validation.rules;

import io.libra.validation.entities.ValidationContext;
import io.libra.validation.entities.ValidationFailureReason;

import java.util.Optional;

// Vérifie : context.customer().kycLevel() est suffisant pour l'instrument visé.
// Mapping de référence (à formaliser en phase logique) :
//   NONE     -> bloque tout
//   BASIC    -> cash + FX spot petit ticket
//   ENHANCED -> + equity standard
//   FULL     -> + leveraged, derivés
// Tient également compte de clientCategory (RETAIL plus contraint que PROFESSIONAL).
public record KycCheckRule() implements ValidationRule {

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        // Phase 1 : logique métier à venir.
        return Optional.empty();
    }
}
