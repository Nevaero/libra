package io.libra.validation.rules;

import io.libra.validation.entities.ValidationContext;
import io.libra.validation.entities.ValidationFailureReason;

import java.util.Optional;

// Sanity check : pour un LIMIT order, vérifie que le limitPrice n'est pas absurde
// (e.g. > 10× ou < 0.1× le mid courant -> probablement fat-finger error).
// Pas applicable aux MARKET orders (limitPriceMinorUnits = Optional.empty).
// Le seuil exact est configurable (out-of-scope phase 1).
public record LimitPriceSanityCheckRule() implements ValidationRule {

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        // Phase 1 : logique métier à venir.
        return Optional.empty();
    }
}
