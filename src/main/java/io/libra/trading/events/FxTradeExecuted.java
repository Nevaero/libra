package io.libra.trading.events;

import io.libra.trading.entities.Trade;

import java.time.Instant;

public record FxTradeExecuted(Trade trade, Instant occurredAt) implements TradeExecuted {
}
