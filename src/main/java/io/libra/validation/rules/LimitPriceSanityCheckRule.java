package io.libra.validation.rules;

import io.libra.pricing.domain.LatestQuote;
import io.libra.validation.domain.ValidationContext;
import io.libra.validation.domain.ValidationFailureReason;
import io.libra.validation.domain.enums.ValidationFailureCode;

import java.util.Optional;

// Fat-finger guard for LIMIT orders : reject a limit price more than MAX_PRICE_DEVIATION_FACTOR×
// above or below the current mid. N/A to MARKET orders, and skipped when there is no reference quote.
public record LimitPriceSanityCheckRule() implements ValidationRule {

    // A limit beyond this factor either side of the mid is treated as a fat-finger error.
    // Phase 1 : fixed ; a configurable per-instrument band can come later.
    private static final long MAX_PRICE_DEVIATION_FACTOR = 10;

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        Optional<Long> limit = context.request().limitPriceMinorUnits();
        Optional<LatestQuote> quote = context.latestQuote();
        if (limit.isEmpty() || quote.isEmpty()) {
            return Optional.empty();
        }
        long limitPrice = limit.get();
        LatestQuote q = quote.get();
        long mid = (q.bidMinorUnits() + q.askMinorUnits()) / 2;
        if (mid <= 0) {
            return Optional.empty();
        }
        if (limitPrice < mid / MAX_PRICE_DEVIATION_FACTOR
                || limitPrice > mid * MAX_PRICE_DEVIATION_FACTOR) {
            return Optional.of(new ValidationFailureReason(
                    ValidationFailureCode.LIMIT_PRICE_OUT_OF_BOUNDS,
                    "limit " + limitPrice + " out of band vs mid " + mid));
        }
        return Optional.empty();
    }
}
