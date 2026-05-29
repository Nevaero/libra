package io.libra.api.dto;

import io.libra.reference.commands.RegisterCurrencyPairCommand;

import java.util.UUID;

public record RegisterCurrencyPairRequest(
        String baseCurrencyCode,
        String quoteCurrencyCode,
        int priceScale,
        UUID providerId
) {

    public RegisterCurrencyPairCommand toCommand() {
        return new RegisterCurrencyPairCommand(baseCurrencyCode, quoteCurrencyCode, priceScale, providerId);
    }
}
