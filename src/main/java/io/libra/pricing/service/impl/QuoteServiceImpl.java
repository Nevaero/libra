package io.libra.pricing.service.impl;

import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.events.PriceTick;
import io.libra.pricing.events.QuoteAdvanced;
import io.libra.pricing.persistence.mapper.LatestQuoteMapper;
import io.libra.pricing.repository.LatestQuoteRepository;
import io.libra.pricing.service.QuoteService;
import io.libra.util.Uuids;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {

    private final LatestQuoteRepository latestQuoteRepository;

    private final LatestQuoteMapper latestQuoteMapper;

    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public void ingestTick(PriceTick tick) {
        // One atomic statement decides apply-vs-drop : the surrogate id is only used on a
        // first insert ; on conflict the existing row keeps its id and is updated only if the
        // incoming sequence wins. Tenor is passed as its String name for the native query.
        int applied = latestQuoteRepository.upsertIfNewer(
                Uuids.newId(),
                tick.instrumentId(),
                tick.tenor().name(),
                tick.bidMinorUnits(),
                tick.askMinorUnits(),
                tick.bidSize(),
                tick.askSize(),
                tick.priceScale(),
                tick.quoteTime(),
                tick.receivedAt(),
                tick.providerId(),
                tick.sequence(),
                tick.lastPriceMinorUnits(),
                tick.lastSize());

        // Emit only when the quote actually advanced — same transaction as the upsert, so the
        // event and the projection commit atomically (no event for a write that did not happen).
        if (applied > 0) {
            events.publishEvent(new QuoteAdvanced(
                    tick.instrumentId(),
                    tick.tenor(),
                    tick.bidMinorUnits(),
                    tick.askMinorUnits(),
                    tick.bidSize(),
                    tick.askSize(),
                    tick.priceScale(),
                    tick.quoteTime(),
                    tick.sequence(),
                    tick.lastPriceMinorUnits(),
                    tick.lastSize()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LatestQuote> getLatestQuote(UUID instrumentId, Tenor tenor) {
        return latestQuoteRepository.findByInstrumentIdAndTenor(instrumentId, tenor)
                .map(latestQuoteMapper::toDomain);
    }
}
