package io.libra.pricing;

import io.libra.TestcontainersConfiguration;
import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.events.PriceTick;
import io.libra.pricing.events.QuoteAdvanced;
import io.libra.pricing.service.QuoteService;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// QuoteService ingestion against a real PostgreSQL 18 : exercises the native optimistic
// upsert (apply / drop stale / idempotent replay / advance) and the conditional QuoteAdvanced
// emission. Uses seeded reference data (EUR/USD, USD/CHF pairs ; LIBRA_SIM provider).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class QuoteServiceIntegrationTest {

    private static final UUID EUR_USD = UUID.fromString("0190a000-0000-7000-8000-000000000001");
    private static final UUID USD_CHF = UUID.fromString("0190a000-0000-7000-8000-000000000002");
    private static final UUID LIBRA_SIM = UUID.fromString("0190a000-0001-7000-8000-000000000001");

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private ApplicationEvents events;

    @Test
    void appliesDropsStaleReplaysAndAdvances() {
        quoteService.ingestTick(tick(EUR_USD, 10, 108_500, 108_520));
        assertThat(quoteService.getLatestQuote(EUR_USD, Tenor.SPOT)).get()
                .satisfies(q -> {
                    assertThat(q.sequence()).isEqualTo(10);
                    assertThat(q.bidMinorUnits()).isEqualTo(108_500);
                });

        // Out-of-order (lower sequence) → dropped, projection unchanged.
        quoteService.ingestTick(tick(EUR_USD, 5, 999, 1_000));
        assertThat(quoteService.getLatestQuote(EUR_USD, Tenor.SPOT)).get()
                .extracting(LatestQuote::sequence).isEqualTo(10L);

        // Replay (equal sequence) → no-op, idempotent.
        quoteService.ingestTick(tick(EUR_USD, 10, 777, 778));
        assertThat(quoteService.getLatestQuote(EUR_USD, Tenor.SPOT)).get()
                .extracting(LatestQuote::bidMinorUnits).isEqualTo(108_500L);

        // Strictly newer → advances.
        quoteService.ingestTick(tick(EUR_USD, 20, 108_600, 108_620));
        assertThat(quoteService.getLatestQuote(EUR_USD, Tenor.SPOT)).get()
                .satisfies(q -> {
                    assertThat(q.sequence()).isEqualTo(20);
                    assertThat(q.bidMinorUnits()).isEqualTo(108_600);
                });

        // Only the two advancing ticks emitted QuoteAdvanced ; stale + replay emitted nothing.
        assertThat(events.stream(QuoteAdvanced.class)).hasSize(2);
    }

    @Test
    void getLatestQuoteIsEmptyForAnInstrumentNeverTicked() {
        assertThat(quoteService.getLatestQuote(USD_CHF, Tenor.SPOT)).isEmpty();
    }

    private PriceTick tick(UUID instrumentId, long sequence, long bid, long ask) {
        Instant now = Instant.now();
        return new PriceTick(Uuids.newId(), instrumentId, bid, ask, 1_000_000, 1_000_000,
                now, now, LIBRA_SIM, Tenor.SPOT, 5, sequence, null, null);
    }
}
