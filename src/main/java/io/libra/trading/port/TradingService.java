package io.libra.trading.port;

import io.libra.trading.commands.SubmitOrderCommand;
import io.libra.trading.domain.Order;

import java.util.Optional;
import java.util.UUID;

// Module port for trading : the order entry point. submitOrder runs the synchronous command path
// — idempotency, pre-trade validation, execution simulation, Delivery-versus-Payment booking, and
// T+2 settlement scheduling — and returns the order in its terminal phase-1 state
// (EXECUTED, REJECTED or CANCELLED). The SETTLED transition happens later, off the T+2 batch.
public interface TradingService {

    Order submitOrder(SubmitOrderCommand cmd);

    Optional<Order> findOrder(UUID orderId);
}
