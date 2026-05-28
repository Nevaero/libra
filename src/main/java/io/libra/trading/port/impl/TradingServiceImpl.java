package io.libra.trading.port.impl;

import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Instrument;
import io.libra.core.entities.Security;
import io.libra.core.persistence.resolution.AssetRef;
import io.libra.core.persistence.resolution.AssetResolver;
import io.libra.core.persistence.resolution.InstrumentRef;
import io.libra.core.persistence.resolution.InstrumentResolver;
import io.libra.core.persistence.resolution.ReferenceResolution;
import io.libra.ledger.domain.JournalEntry;
import io.libra.settlement.domain.enums.AssetClass;
import io.libra.settlement.port.SettlementService;
import io.libra.trading.commands.SubmitOrderCommand;
import io.libra.trading.domain.Order;
import io.libra.trading.domain.Trade;
import io.libra.trading.domain.enums.OrderStatus;
import io.libra.trading.events.EquityTradeExecuted;
import io.libra.trading.events.FxTradeExecuted;
import io.libra.trading.events.OrderAccepted;
import io.libra.trading.events.OrderCancelled;
import io.libra.trading.events.OrderRejected;
import io.libra.trading.events.OrderSubmitted;
import io.libra.trading.internal.ExecutionSimulator;
import io.libra.trading.internal.TradeBooker;
import io.libra.trading.persistence.entity.OrderEntity;
import io.libra.trading.persistence.mapper.OrderMapper;
import io.libra.trading.persistence.mapper.TradeMapper;
import io.libra.trading.port.TradingService;
import io.libra.trading.repository.OrderRepository;
import io.libra.trading.repository.TradeRepository;
import io.libra.util.Uuids;
import io.libra.validation.domain.Rejected;
import io.libra.validation.domain.ValidationFailureReason;
import io.libra.validation.domain.ValidationRequest;
import io.libra.validation.domain.ValidationResult;
import io.libra.validation.port.ValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// Order orchestrator : the synchronous command path. Every step runs in one transaction, so a
// failure rolls the whole submission back ; business outcomes (rejected, no-fill) are persisted as
// terminal order states, not exceptions. Phase 1 stops at EXECUTED + booked + T+2-scheduled ; the
// SETTLED transition is driven later, off the settlement batch.
@Service
@RequiredArgsConstructor
public class TradingServiceImpl implements TradingService {

    private final OrderRepository orderRepository;

    private final OrderMapper orderMapper;

    private final TradeRepository tradeRepository;

    private final TradeMapper tradeMapper;

    private final ValidationService validationService;

    private final ExecutionSimulator executionSimulator;

    private final TradeBooker tradeBooker;

    private final SettlementService settlementService;

    private final ReferenceResolution referenceResolution;

    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public Order submitOrder(SubmitOrderCommand cmd) {
        // 1. Idempotency : a replayed (clientId, idempotencyKey) returns the original order.
        Optional<OrderEntity> existing =
                orderRepository.findByClientIdAndIdempotencyKey(cmd.clientId(), cmd.idempotencyKey());
        if (existing.isPresent()) {
            return toDomain(existing.get());
        }

        // 2. Record the order as SUBMITTED.
        Order order = save(new Order(
                Uuids.newId(), cmd.idempotencyKey(), cmd.clientId(), Instant.now(),
                cmd.instrument(), cmd.side(), cmd.quantity(), OrderStatus.SUBMITTED,
                cmd.orderType(), cmd.limitPriceMinorUnits().orElse(null), null));
        events.publishEvent(new OrderSubmitted(order, Instant.now()));

        // 3. Pre-trade gate.
        ValidationResult result = validationService.validate(new ValidationRequest(
                order.id(), order.clientId(), order.instrument(), order.side(),
                order.quantity(), cmd.limitPriceMinorUnits()));
        if (result instanceof Rejected rejected) {
            Order rejectedOrder = save(transition(order, OrderStatus.REJECTED));
            events.publishEvent(new OrderRejected(rejectedOrder, reasonText(rejected), Instant.now()));
            return rejectedOrder;
        }
        order = save(transition(order, OrderStatus.ACCEPTED));
        events.publishEvent(new OrderAccepted(order, Instant.now()));

        // 4. Execute against the simulated venue.
        Optional<ExecutionSimulator.Fill> maybeFill = executionSimulator.execute(order);
        if (maybeFill.isEmpty()) {
            Order cancelled = save(transition(order, OrderStatus.CANCELLED));
            events.publishEvent(new OrderCancelled(cancelled, "no marketable price", Instant.now()));
            return cancelled;
        }
        ExecutionSimulator.Fill fill = maybeFill.get();

        // 5. Trade → DvP booking (T+0 pending) → T+2 settlement instruction.
        Trade trade = new Trade(Uuids.newId(), order.id(), fill.counterpartyId(),
                fill.executedQuantity(), fill.executedPriceMinorUnits(), Instant.now());
        tradeRepository.save(tradeMapper.toEntity(trade));

        JournalEntry booking = tradeBooker.book(order, trade, fill.priceScale());
        settlementService.scheduleSettlement(trade.id(), booking.id(),
                LocalDate.now(ZoneOffset.UTC), assetClassOf(order.instrument()));

        Order executed = save(transition(order, OrderStatus.EXECUTED));
        events.publishEvent(tradeExecuted(order.instrument(), trade));
        return executed;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findOrder(UUID orderId) {
        return orderRepository.findById(orderId).map(this::toDomain);
    }

    private Order save(Order order) {
        orderRepository.save(orderMapper.toEntity(order));
        return order;
    }

    private Order transition(Order order, OrderStatus target) {
        if (!order.status().canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal order transition " + order.status() + " → " + target + " for " + order.id());
        }
        return new Order(order.id(), order.idempotencyKey(), order.clientId(), order.submittedAt(),
                order.instrument(), order.side(), order.quantity(), target, order.orderType(),
                order.limitPriceMinorUnits(), order.parentOrderId());
    }

    private Order toDomain(OrderEntity entity) {
        AssetResolver assetResolver = referenceResolution.assetResolverFor(List.of(new AssetRef(
                entity.getQuantity().getAssetType(), entity.getQuantity().getAssetCode(),
                entity.getQuantity().getAssetMic())));
        InstrumentResolver instrumentResolver = referenceResolution.instrumentResolverFor(
                List.of(new InstrumentRef(entity.getInstrumentType(), entity.getInstrumentId())));
        return orderMapper.toDomain(entity, instrumentResolver, assetResolver);
    }

    private String reasonText(Rejected rejected) {
        return rejected.reasons().stream()
                .map(r -> r.code() + ":" + r.detail())
                .collect(Collectors.joining("; "));
    }

    private AssetClass assetClassOf(Instrument instrument) {
        return switch (instrument) {
            case CurrencyPair _ -> AssetClass.FX;
            case Security _ -> AssetClass.EQUITY;
        };
    }

    private Object tradeExecuted(Instrument instrument, Trade trade) {
        Instant now = Instant.now();
        return switch (instrument) {
            case CurrencyPair _ -> new FxTradeExecuted(trade, now);
            case Security _ -> new EquityTradeExecuted(trade, now);
        };
    }
}
