package io.libra.api.controller;

import io.libra.api.dto.OrderResponse;
import io.libra.api.dto.SubmitOrderRequest;
import io.libra.core.entities.Instrument;
import io.libra.reference.port.ReferenceDataService;
import io.libra.trading.port.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;

    private final ReferenceDataService referenceData;

    // Resolves the instrument id from the body, then delegates to the order orchestrator.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse submit(@RequestBody SubmitOrderRequest request) {
        Instrument instrument = referenceData.findInstrument(request.instrumentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown instrument: " + request.instrumentId()));
        return OrderResponse.from(tradingService.submitOrder(request.toCommand(instrument)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id) {
        return tradingService.findOrder(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
