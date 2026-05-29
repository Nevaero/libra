package io.libra.api.dto;

import io.libra.core.entities.Instrument;
import io.libra.core.entities.Money;
import io.libra.core.entities.enums.Side;
import io.libra.trading.commands.SubmitOrderCommand;
import io.libra.trading.domain.enums.OrderType;
import io.libra.util.Uuids;

import java.util.Optional;
import java.util.UUID;

// The client sends an instrument id and a plain quantity; the controller resolves the Instrument
// and the quantity's asset before building the command. idempotencyKey is optional: a missing key
// is generated, though a client that wants safe retries should supply its own.
public record SubmitOrderRequest(
        UUID idempotencyKey,
        UUID clientId,
        UUID instrumentId,
        Side side,
        long quantityMinorUnits,
        OrderType orderType,
        Long limitPriceMinorUnits
) {

    public SubmitOrderCommand toCommand(Instrument instrument) {
        UUID key = idempotencyKey != null ? idempotencyKey : Uuids.newId();
        Money quantity = new Money(quantityMinorUnits, instrument.baseAsset());
        return new SubmitOrderCommand(
                key, clientId, instrument, side, quantity, orderType,
                Optional.ofNullable(limitPriceMinorUnits));
    }
}
