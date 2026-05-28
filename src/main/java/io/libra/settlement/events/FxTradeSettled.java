package io.libra.settlement.events;

import java.time.Instant;
import java.util.UUID;

public record FxTradeSettled(
        UUID tradeId,
        UUID settlementInstructionId,
        Instant occurredAt
) implements TradeSettled {
}
