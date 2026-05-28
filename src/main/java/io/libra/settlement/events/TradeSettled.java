package io.libra.settlement.events;

import java.time.Instant;
import java.util.UUID;

// Sealed cohérent avec TradeExecuted côté trading.
// Distinction sémantique pour le routing Kafka (deux topics distincts à terme) et pour le pattern matching exhaustif.
public sealed interface TradeSettled permits FxTradeSettled, EquityTradeSettled {

    UUID tradeId();

    UUID settlementInstructionId();

    Instant occurredAt();
}
