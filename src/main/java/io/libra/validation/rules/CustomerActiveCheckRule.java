package io.libra.validation.rules;

import io.libra.customer.domain.enums.CustomerStatus;
import io.libra.validation.domain.ValidationContext;
import io.libra.validation.domain.ValidationFailureReason;
import io.libra.validation.domain.enums.ValidationFailureCode;

import java.util.Optional;

// The customer must be ACTIVE. PENDING_KYC / SUSPENDED / CLOSED block the trade.
public record CustomerActiveCheckRule() implements ValidationRule {

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        CustomerStatus status = context.customer().status();
        if (status != CustomerStatus.ACTIVE) {
            return Optional.of(new ValidationFailureReason(
                    ValidationFailureCode.CUSTOMER_NOT_ACTIVE, "customer status is " + status));
        }
        return Optional.empty();
    }
}
