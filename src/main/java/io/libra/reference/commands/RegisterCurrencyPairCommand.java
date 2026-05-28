package io.libra.reference.commands;

import java.util.UUID;

public record RegisterCurrencyPairCommand(
        String baseCurrencyCode,
        String quoteCurrencyCode,
        int priceScale,
        UUID providerId
) { }
