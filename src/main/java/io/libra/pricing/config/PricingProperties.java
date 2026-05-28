package io.libra.pricing.config;

import io.libra.pricing.domain.enums.Tenor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// Binds the subscription config (pricing-subscriptions.yml) under `libra.pricing`. Each provider
// is a price source ; each subscription identifies an instrument by business identity — ISIN+MIC
// for a security, base/quote ISO 4217 codes for an FX pair — plus the symbol THIS provider uses.
@ConfigurationProperties(prefix = "libra.pricing")
public record PricingProperties(List<ProviderConfig> providers) {

    public enum ProviderType { FIX, OANDA }

    public record ProviderConfig(
            String code,
            String name,
            ProviderType type,
            List<SubscriptionConfig> subscriptions
    ) { }

    public record SubscriptionConfig(
            // Security identity (ISO 6166 + ISO 10383). Mutually exclusive with base/quote.
            String isin,
            String mic,
            // FX pair identity (ISO 4217 codes).
            String base,
            String quote,
            // The symbol THIS provider uses for the instrument (e.g. "EUR/USD" vs "EUR_USD").
            String symbol,
            Tenor tenor
    ) {
        public boolean isSecurity() {
            return isin != null;
        }
    }
}
