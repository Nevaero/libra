package io.libra.trading.entities;

import io.libra.core.entities.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Execution record matched against a counterparty. A single Order can generate N Trades
// (case of a LIMIT partial fill).
public record Trade(
        UUID id,
        UUID orderId,
        UUID counterpartyId,
        Money executedQuantity,
        long executedPriceMinorUnits,
        Instant executedAt
) {

    public Trade {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(counterpartyId, "counterpartyId must not be null");
        Objects.requireNonNull(executedQuantity, "executedQuantity must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");
        if (executedQuantity.minorUnits() <= 0) {
            throw new IllegalArgumentException(
                    "Executed quantity must be positive, got: " + executedQuantity.minorUnits());
        }
        if (executedPriceMinorUnits <= 0) {
            throw new IllegalArgumentException(
                    "Executed price must be positive, got: " + executedPriceMinorUnits);
        }
    }
}
