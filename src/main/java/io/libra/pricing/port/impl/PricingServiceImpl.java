package io.libra.pricing.port.impl;

import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.port.PricingService;
import io.libra.pricing.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final QuoteService quoteService;

    @Override
    public Optional<LatestQuote> getLatestQuote(UUID instrumentId, Tenor tenor) {
        return quoteService.getLatestQuote(instrumentId, tenor);
    }
}
