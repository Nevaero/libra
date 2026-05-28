package io.libra.validation.rules;

import io.libra.core.entities.Instrument;
import io.libra.core.entities.enums.Side;
import io.libra.pricing.domain.LatestQuote;
import io.libra.validation.domain.ValidationContext;
import io.libra.validation.domain.ValidationFailureReason;
import io.libra.validation.domain.ValidationRequest;
import io.libra.validation.domain.enums.ValidationFailureCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

// availableBalance(sourceBalance) >= the order's exposure, both in the spent asset's minor units.
//   SELL : exposure = base quantity (the client gives the base).
//   BUY  : exposure = quantity × price, in the quote asset (limit price if any, else the ask).
// Returns empty when a BUY cannot be priced (no quote) — that case is left to other rules.
public record BalanceCheckRule() implements ValidationRule {

    @Override
    public Optional<ValidationFailureReason> validate(ValidationContext context) {
        Long exposure = exposureMinorUnits(context);
        if (exposure == null) {
            return Optional.empty();
        }
        long available = context.sourceBalance().availableBalance().minorUnits();
        if (available < exposure) {
            return Optional.of(new ValidationFailureReason(
                    ValidationFailureCode.INSUFFICIENT_FUNDS,
                    "available " + available + " < required " + exposure
                            + " " + context.sourceBalance().asset().code()));
        }
        return Optional.empty();
    }

    private Long exposureMinorUnits(ValidationContext context) {
        ValidationRequest request = context.request();
        if (request.side() == Side.SELL) {
            return request.quantity().minorUnits();
        }
        Optional<LatestQuote> quote = context.latestQuote();
        if (quote.isEmpty()) {
            return null;
        }
        long priceMinor = request.limitPriceMinorUnits().orElse(quote.get().askMinorUnits());
        int priceScale = quote.get().priceScale();
        Instrument instrument = request.instrument();

        // exposure_quote = (qtyBase / 10^baseDec) × (price / 10^priceScale) × 10^quoteDec, rounded
        // UP so a borderline order is never approved on rounding.
        BigDecimal baseQty = BigDecimal.valueOf(request.quantity().minorUnits())
                .movePointLeft(instrument.baseAsset().decimals());
        BigDecimal price = BigDecimal.valueOf(priceMinor).movePointLeft(priceScale);
        return baseQty.multiply(price)
                .movePointRight(instrument.quoteAsset().decimals())
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }
}
