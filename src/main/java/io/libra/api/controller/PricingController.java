package io.libra.api.controller;

import io.libra.api.dto.response.QuoteResponse;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.port.PricingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Quotes", description = "Latest market quotes")
@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    @GetMapping("/{instrumentId}")
    public ResponseEntity<QuoteResponse> latest(@PathVariable UUID instrumentId,
                                                 @RequestParam(defaultValue = "SPOT") Tenor tenor) {
        return pricingService.getLatestQuote(instrumentId, tenor)
                .map(QuoteResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
