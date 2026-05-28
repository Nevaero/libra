package io.libra.pricing;

import io.libra.TestcontainersConfiguration;
import io.libra.pricing.client.impl.FixPriceProviderClient;
import io.libra.pricing.client.impl.OandaPriceProviderClient;
import io.libra.pricing.client.Subscription;
import io.libra.pricing.client.model.fix.FixMarketDataSnapshot;
import io.libra.pricing.client.model.fix.FixMdEntry;
import io.libra.pricing.client.model.json.OandaPrice;
import io.libra.pricing.client.model.json.OandaPriceBucket;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Adapters end-to-end : a raw provider message (FIX 4.4 / OANDA v20) is normalized into a
// PriceTick and ingested into the LatestQuote read-model. Uses seeded reference data and
// instruments distinct from QuoteServiceIntegrationTest (the container is shared) to avoid
// cross-provider sequence interference.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PriceProviderClientIntegrationTest {

    private static final UUID GBP_USD = UUID.fromString("0190a000-0000-7000-8000-000000000005");
    private static final UUID USD_JPY = UUID.fromString("0190a000-0000-7000-8000-000000000004");
    private static final UUID AAPL = UUID.fromString("0190a000-0003-7000-8000-000000000002");   // seeded equity
    private static final UUID PROVIDER = UUID.fromString("0190a000-0001-7000-8000-000000000001");

    @Autowired
    private QuoteService quoteService;

    @Test
    void fixClientNormalizesSnapshotIntoLatestQuote() {
        FixPriceProviderClient client = new FixPriceProviderClient(quoteService, PROVIDER);
        client.subscribe(new Subscription(GBP_USD, "GBP/USD", Tenor.SPOT, 5));

        client.onSnapshot(new FixMarketDataSnapshot(
                "ACME_BANK", "LIBRA", 4242L, "20260528-10:00:00.000", "REQ-1", "GBP/USD",
                List.of(new FixMdEntry('0', "1.27500", "1000000", null),
                        new FixMdEntry('1', "1.27520", "1000000", null))));

        assertThat(quoteService.getLatestQuote(GBP_USD, Tenor.SPOT)).get().satisfies(q -> {
            assertThat(q.bidMinorUnits()).isEqualTo(127_500);
            assertThat(q.askMinorUnits()).isEqualTo(127_520);
            assertThat(q.priceScale()).isEqualTo(5);
            assertThat(q.sequence()).isEqualTo(4242);   // FIX MsgSeqNum passthrough
        });
    }

    @Test
    void oandaClientNormalizesPriceIntoLatestQuote() {
        OandaPriceProviderClient client = new OandaPriceProviderClient(quoteService, PROVIDER);
        client.subscribe(new Subscription(USD_JPY, "USD_JPY", Tenor.SPOT, 5));

        client.onPrice(new OandaPrice("PRICE", "USD_JPY", "2026-05-28T10:00:01Z",
                List.of(new OandaPriceBucket("1.50120", 1_000_000)),
                List.of(new OandaPriceBucket("1.50140", 1_000_000)),
                "1.50100", "1.50160", "tradeable", true));

        assertThat(quoteService.getLatestQuote(USD_JPY, Tenor.SPOT)).get().satisfies(q -> {
            assertThat(q.bidMinorUnits()).isEqualTo(150_120);
            assertThat(q.askMinorUnits()).isEqualTo(150_140);
            assertThat(q.sequence()).isPositive();   // derived from quoteTime
        });
    }

    @Test
    void oandaHeartbeatIsIgnoredGracefully() {
        OandaPriceProviderClient client = new OandaPriceProviderClient(quoteService, PROVIDER);
        // A heartbeat has type != "PRICE" and no bids/asks ; it must be a no-op, never an NPE.
        assertThatCode(() -> client.onPrice(
                new OandaPrice("HEARTBEAT", null, "2026-05-28T10:00:02Z",
                        null, null, null, null, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void fixEquityCapturesLastTradeAndPreservesItAcrossQuoteOnlyUpdates() {
        FixPriceProviderClient client = new FixPriceProviderClient(quoteService, PROVIDER);
        client.subscribe(new Subscription(AAPL, "AAPL", Tenor.SPOT, 2));

        // Equity snapshot carrying bid (0), ask (1) AND a last trade (2).
        client.onSnapshot(new FixMarketDataSnapshot(
                "XNAS_FEED", "LIBRA", 50L, "20260528-15:30:00.000", "REQ-2", "AAPL",
                List.of(new FixMdEntry('0', "150.20", "300", null),
                        new FixMdEntry('1', "150.25", "300", null),
                        new FixMdEntry('2', "150.22", "100", null))));

        assertThat(quoteService.getLatestQuote(AAPL, Tenor.SPOT)).get().satisfies(q -> {
            assertThat(q.bidMinorUnits()).isEqualTo(15_020);
            assertThat(q.askMinorUnits()).isEqualTo(15_025);
            assertThat(q.lastPriceMinorUnits()).isEqualTo(15_022L);
            assertThat(q.lastSize()).isEqualTo(100L);
        });

        // Quote-only update at a higher sequence : bid/ask move, last trade is preserved (COALESCE).
        client.onSnapshot(new FixMarketDataSnapshot(
                "XNAS_FEED", "LIBRA", 51L, "20260528-15:30:01.000", "REQ-2", "AAPL",
                List.of(new FixMdEntry('0', "150.30", "300", null),
                        new FixMdEntry('1', "150.35", "300", null))));

        assertThat(quoteService.getLatestQuote(AAPL, Tenor.SPOT)).get().satisfies(q -> {
            assertThat(q.bidMinorUnits()).isEqualTo(15_030);
            assertThat(q.askMinorUnits()).isEqualTo(15_035);
            assertThat(q.lastPriceMinorUnits()).isEqualTo(15_022L);   // preserved across quote-only tick
            assertThat(q.lastSize()).isEqualTo(100L);
        });
    }
}
