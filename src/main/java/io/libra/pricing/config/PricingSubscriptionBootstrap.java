package io.libra.pricing.config;

import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Security;
import io.libra.pricing.client.PriceProviderClient;
import io.libra.pricing.client.PriceProviderClientRegistry;
import io.libra.pricing.client.Subscription;
import io.libra.pricing.client.impl.FixPriceProviderClient;
import io.libra.pricing.client.impl.OandaPriceProviderClient;
import io.libra.pricing.config.PricingProperties.ProviderConfig;
import io.libra.pricing.config.PricingProperties.SubscriptionConfig;
import io.libra.pricing.persistence.entity.ProviderEntity;
import io.libra.pricing.repository.ProviderRepository;
import io.libra.pricing.service.QuoteService;
import io.libra.reference.port.ReferenceDataService;
import io.libra.util.Uuids;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Composition root for the inbound price adapters. At startup : ensures each configured provider
// exists, instantiates one client per source, resolves every subscription's instrument by its
// business identity via the Security Master (→ instrumentId + intrinsic price scale), wires it,
// and exposes the clients in a registry. Fail-fast : a subscription to an unlisted instrument
// blows up here, not in production.
@Configuration
@EnableConfigurationProperties(PricingProperties.class)
@RequiredArgsConstructor
public class PricingSubscriptionBootstrap {

    private final PricingProperties properties;

    private final QuoteService quoteService;

    private final ReferenceDataService referenceData;

    private final ProviderRepository providerRepository;

    @Bean
    public PriceProviderClientRegistry priceProviderClients() {
        Map<String, PriceProviderClient> clients = new LinkedHashMap<>();
        for (ProviderConfig providerConfig : properties.providers()) {
            UUID providerId = ensureProvider(providerConfig);
            PriceProviderClient client = switch (providerConfig.type()) {
                case FIX -> new FixPriceProviderClient(quoteService, providerId);
                case OANDA -> new OandaPriceProviderClient(quoteService, providerId);
            };
            providerConfig.subscriptions().forEach(s -> client.subscribe(toSubscription(s)));
            clients.put(providerConfig.code(), client);
        }
        return new PriceProviderClientRegistry(clients);
    }

    // Providers are config-driven : resolve the provider row by its unique code, create it if
    // missing. (Each repository call runs in its own transaction.)
    private UUID ensureProvider(ProviderConfig config) {
        return providerRepository.findByCode(config.code())
                .map(ProviderEntity::getId)
                .orElseGet(() -> providerRepository
                        .save(new ProviderEntity(Uuids.newId(), config.name(), config.code()))
                        .getId());
    }

    // Resolve the instrument by its business identity → instrumentId + intrinsic price scale.
    private Subscription toSubscription(SubscriptionConfig config) {
        if (config.isSecurity()) {
            Security security = referenceData.findSecurityByIsinAndMic(config.isin(), config.mic())
                    .orElseThrow(() -> new IllegalStateException(
                            "No security for ISIN=" + config.isin() + " MIC=" + config.mic()));
            // Equity price scale = the decimals of its quote currency (e.g. USD/CHF → 2).
            return new Subscription(security.id(), config.symbol(), config.tenor(),
                    security.quoteCurrency().decimals());
        }
        CurrencyPair pair = referenceData.findPairByCodes(config.base(), config.quote())
                .orElseThrow(() -> new IllegalStateException(
                        "No currency pair for " + config.base() + "/" + config.quote()));
        return new Subscription(pair.id(), config.symbol(), config.tenor(), pair.priceScale());
    }
}
