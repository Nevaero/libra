package io.libra.validation.rules;

import io.libra.customer.domain.enums.KycLevel;
import io.libra.validation.domain.ValidationContext;
import io.libra.validation.domain.ValidationFailureReason;
import io.libra.validation.domain.enums.ValidationFailureCode;

import java.util.Optional;

// Phase 1 : any trade requires a completed KYC (level != NONE). Finer per-instrument tiers
// (BASIC for FX spot, ENHANCED for equity, FULL for leveraged) are deferred.
public record KycCheckRule() implements ValidationRule {

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        if (context.customer().kycLevel() == KycLevel.NONE) {
            return Optional.of(new ValidationFailureReason(
                    ValidationFailureCode.KYC_INSUFFICIENT, "KYC not completed (level NONE)"));
        }
        return Optional.empty();
    }
}
