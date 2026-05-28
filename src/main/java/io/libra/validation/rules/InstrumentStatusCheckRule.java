package io.libra.validation.rules;

import io.libra.validation.entities.ValidationContext;
import io.libra.validation.entities.ValidationFailureReason;

import java.util.Optional;

// Vérifie : l'instrument est dans un statut autorisé au trading.
//   Security : ACTIVE uniquement (PENDING_LISTING, SUSPENDED, HALTED, DELISTED -> rejet)
//   CurrencyPair : ACTIVE uniquement (SUSPENDED, DEACTIVATED -> rejet)
// Lookup nécessaire via pricing (l'instrument arrive dans request, mais son status courant
// doit être lu depuis le référentiel pricing — pas une snapshot embarquée).
public record InstrumentStatusCheckRule() implements ValidationRule {

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        // Phase 1 : logique métier à venir.
        return Optional.empty();
    }
}
