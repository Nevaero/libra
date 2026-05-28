package io.libra.trading.commands;

import io.libra.core.entities.Instrument;
import io.libra.core.entities.Money;
import io.libra.core.entities.enums.Side;
import io.libra.trading.domain.enums.OrderType;

import java.util.Optional;
import java.util.UUID;

// Client intent to trade. `idempotencyKey` is client-supplied : re-submitting the same key for
// the same client returns the original order instead of placing a second one.
public record SubmitOrderCommand(
        UUID idempotencyKey,
        UUID clientId,
        Instrument instrument,
        Side side,
        Money quantity,
        OrderType orderType,
        // empty for a MARKET order ; present (minor units, scale of the instrument) for a LIMIT order.
        Optional<Long> limitPriceMinorUnits
) {
}
