package io.libra.trading.events;

import io.libra.trading.domain.Trade;

import java.time.Instant;

public sealed interface TradeExecuted permits FxTradeExecuted, EquityTradeExecuted {

    Trade trade();

    Instant occurredAt();
}
