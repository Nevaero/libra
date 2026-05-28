package io.libra.trading.entities;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Money;
import io.libra.core.entities.enums.Side;
import io.libra.trading.entities.enums.OrderStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Aggregated client intent (e.g. "buy EURUSD for 1000 USD"), decomposed by the execution
// engine into multiple child Orders.
public record ParentOrder(
        UUID id,
        UUID idempotencyKey,
        UUID clientId,
        Instant submittedAt,
        Side side,
        Money targetQuantity,
        Asset sourceAsset,
        OrderStatus status
) {

    public ParentOrder {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(targetQuantity, "targetQuantity must not be null");
        Objects.requireNonNull(sourceAsset, "sourceAsset must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (targetQuantity.minorUnits() <= 0) {
            throw new IllegalArgumentException(
                    "Target quantity must be positive, got: " + targetQuantity.minorUnits());
        }
        if (Objects.equals(targetQuantity.asset(), sourceAsset)) {
            throw new IllegalArgumentException(
                    "Target asset must differ from source asset, both are: " + sourceAsset);
        }
    }
}
