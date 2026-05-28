package io.libra.validation.rules;

import io.libra.validation.domain.ValidationContext;
import io.libra.validation.domain.ValidationFailureReason;

import java.util.Optional;

// Chain of Responsibility : chaque règle est appliquée indépendamment au contexte enrichi.
// Le service de validation collecte les `Optional.of(...)` retournés et construit un Rejected
// avec la liste agrégée. Si toutes les règles renvoient Optional.empty(), l'ordre est Approved.
public sealed interface ValidationRule
        permits BalanceCheckRule,
                CustomerActiveCheckRule,
                KycCheckRule,
                InstrumentStatusCheckRule,
                LimitPriceSanityCheckRule {

    Optional<ValidationFailureReason> validate(ValidationContext context);
}
