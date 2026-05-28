package io.libra.trading.events;

import io.libra.trading.entities.Trade;

import java.time.Instant;

public sealed interface TradeExecuted permits FxTradeExecuted, EquityTradeExecuted {

    Trade trade();

    Instant occurredAt();
}
