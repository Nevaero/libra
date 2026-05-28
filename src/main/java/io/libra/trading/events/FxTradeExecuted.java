package io.libra.trading.events;

import io.libra.trading.domain.Trade;

import java.time.Instant;

public record FxTradeExecuted(Trade trade, Instant occurredAt) implements TradeExecuted {
}
