package io.libra.validation.rules;

import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Instrument;
import io.libra.core.entities.Security;
import io.libra.core.entities.enums.CurrencyPairStatus;
import io.libra.core.entities.enums.SecurityStatus;
import io.libra.validation.domain.ValidationContext;
import io.libra.validation.domain.ValidationFailureReason;
import io.libra.validation.domain.enums.ValidationFailureCode;

import java.util.Optional;

// The instrument must be tradable : ACTIVE for both a Security and a CurrencyPair. Any other
// status (PENDING_LISTING / SUSPENDED / HALTED / DELISTED ; DEACTIVATED for a pair) blocks.
// The status is read from the instrument carried by the request (resolved upstream).
public record InstrumentStatusCheckRule() implements ValidationRule {

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        Instrument instrument = context.request().instrument();
        boolean tradable = switch (instrument) {
            case Security s -> s.status() == SecurityStatus.ACTIVE;
            case CurrencyPair cp -> cp.status() == CurrencyPairStatus.ACTIVE;
        };
        if (!tradable) {
            return Optional.of(new ValidationFailureReason(
                    ValidationFailureCode.INSTRUMENT_NOT_TRADABLE, "instrument not tradable"));
        }
        return Optional.empty();
    }
}
