package io.libra;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// Binds the test-only reference data declared under `libra.test` in src/test/resources/application.yml.
@ConfigurationProperties(prefix = "libra.test")
public record TestDataProperties(List<CurrencyPairSeed> currencyPairs) {

    public record CurrencyPairSeed(String base, String quote) {
    }
}
