package io.libra.pricing;

import io.libra.TestcontainersConfiguration;
import io.libra.pricing.client.PriceProviderClientRegistry;
import io.libra.pricing.client.impl.FixPriceProviderClient;
import io.libra.pricing.client.impl.OandaPriceProviderClient;
import io.libra.pricing.client.model.fix.FixMarketDataSnapshot;
import io.libra.pricing.client.model.fix.FixMdEntry;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The YAML-driven bootstrap : pricing-subscriptions.yml is bound, one client per provider is
// built, and each subscription is resolved by business identity (ISIN+MIC / base+quote) via the
// Security Master. Feeding the resolved subscription lands on the right instrument with the
// price scale derived from reference data.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PricingSubscriptionBootstrapTest {

    // NESN on SIX (seeded) — the security the ACME_FIX provider subscribes to in the YAML.
    private static final UUID NESN = UUID.fromString("0190a000-0003-7000-8000-000000000001");

    @Autowired
    private PriceProviderClientRegistry registry;

    @Autowired
    private QuoteService quoteService;

    @Test
    void bootstrapBuildsClientsPerProviderFromConfig() {
        assertThat(registry.get("ACME_FIX")).isInstanceOf(FixPriceProviderClient.class);
        assertThat(registry.get("OANDA_DEMO")).isInstanceOf(OandaPriceProviderClient.class);
    }

    @Test
    void resolvedSubscriptionRoutesAFeedToTheRightInstrument() {
        FixPriceProviderClient acme = (FixPriceProviderClient) registry.get("ACME_FIX");

        // "NESN" was resolved from ISIN CH0038863350 + MIC XSWX at bootstrap, with the price
        // scale derived from its quote currency (CHF → 2).
        acme.onSnapshot(new FixMarketDataSnapshot(
                "ACME_BANK", "LIBRA", 7L, "20260528-09:00:00.000", "REQ-9", "NESN",
                List.of(new FixMdEntry('0', "120.50", "500", null),
                        new FixMdEntry('1', "120.60", "500", null))));

        assertThat(quoteService.getLatestQuote(NESN, Tenor.SPOT)).get().satisfies(q -> {
            assertThat(q.bidMinorUnits()).isEqualTo(12_050);
            assertThat(q.askMinorUnits()).isEqualTo(12_060);
            assertThat(q.priceScale()).isEqualTo(2);
        });
    }
}
