package io.libra.validation.entities;

import io.libra.core.entities.Instrument;
import io.libra.core.entities.Money;
import io.libra.core.entities.enums.Side;

import java.util.Optional;
import java.util.UUID;

public record ValidationRequest(
        UUID orderId,
        UUID clientId,
        Instrument instrument,
        Side side,
        Money quantity,
        // empty pour un MARKET order ; présent pour un LIMIT order (long minor units, scale dérivé de instrument).
        Optional<Long> limitPriceMinorUnits
) {
}
