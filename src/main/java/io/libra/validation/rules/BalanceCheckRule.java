package io.libra.validation.rules;

import io.libra.validation.domain.ValidationContext;
import io.libra.validation.domain.ValidationFailureReason;

import java.util.Optional;

// Vérifie : context.sourceBalance().availableBalance() >= exposition de l'ordre.
// Pour un MARKET BUY : exposition ≈ quantity × midPrice (lecture de latestQuote).
// Pour un LIMIT BUY  : exposition = quantity × limitPrice (max accepté par le client).
// Pour un SELL       : check sur la position (Money en asset cible), pas sur le cash.
public record BalanceCheckRule() implements ValidationRule {

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        // Phase 1 : logique métier à venir.
        return Optional.empty();
    }
}
