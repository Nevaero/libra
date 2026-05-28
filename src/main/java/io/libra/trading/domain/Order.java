package io.libra.trading.domain;

import io.libra.core.entities.Instrument;
import io.libra.core.entities.Money;
import io.libra.core.entities.enums.Side;
import io.libra.trading.domain.enums.OrderStatus;
import io.libra.trading.domain.enums.OrderType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Unified Order domain record. The MARKET|LIMIT distinction is carried by `orderType`
// (discriminator) and `limitPriceMinorUnits` (non-null iff LIMIT).
public record Order(
        UUID id,
        UUID idempotencyKey,
        UUID clientId,
        Instant submittedAt,
        Instrument instrument,
        Side side,
        Money quantity,
        OrderStatus status,
        OrderType orderType,
        // Non-null iff orderType == LIMIT.
        Long limitPriceMinorUnits,
        // Nullable — standalone Order may have no parent.
        UUID parentOrderId
) {

    public Order {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        Objects.requireNonNull(instrument, "instrument must not be null");
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(orderType, "orderType must not be null");

        switch (orderType) {
            case MARKET -> {
                if (limitPriceMinorUnits != null) {
                    throw new IllegalArgumentException(
                            "MARKET order must have null limitPriceMinorUnits, got: " + limitPriceMinorUnits);
                }
            }
            case LIMIT -> {
                if (limitPriceMinorUnits == null) {
                    throw new IllegalArgumentException("LIMIT order must have non-null limitPriceMinorUnits");
                }
                if (limitPriceMinorUnits <= 0) {
                    throw new IllegalArgumentException(
                            "LIMIT order limit price must be positive, got: " + limitPriceMinorUnits);
                }
            }
        }

        if (quantity.minorUnits() <= 0) {
            throw new IllegalArgumentException(
                    "Order quantity must be positive, got: " + quantity.minorUnits());
        }
        if (!Objects.equals(quantity.asset(), instrument.baseAsset())) {
            throw new IllegalArgumentException(
                    "Order quantity asset (" + quantity.asset()
                            + ") must equal instrument.baseAsset (" + instrument.baseAsset() + ")");
        }
    }
}
