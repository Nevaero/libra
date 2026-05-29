package io.libra.api.dto.response;

import io.libra.core.entities.enums.Side;
import io.libra.core.persistence.resolution.InstrumentRefs;
import io.libra.trading.domain.Order;
import io.libra.trading.domain.enums.OrderStatus;
import io.libra.trading.domain.enums.OrderType;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID clientId,
        UUID instrumentId,
        Side side,
        long quantityMinorUnits,
        String quantityAssetCode,
        OrderStatus status,
        OrderType orderType,
        Long limitPriceMinorUnits,
        Instant submittedAt
) {

    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.id(), o.clientId(), InstrumentRefs.idOf(o.instrument()), o.side(),
                o.quantity().minorUnits(), o.quantity().asset().code(), o.status(), o.orderType(),
                o.limitPriceMinorUnits(), o.submittedAt());
    }
}
