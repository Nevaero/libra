package io.libra.core.entities;

import java.util.Objects;

// Domain record. No JPA annotations. Persistence is handled by CurrencyEntity + CurrencyMapper.
public record Currency(String code, String name, int decimals) implements Asset {

    public Currency {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (decimals < 0 || decimals > 8) {
            throw new IllegalArgumentException("decimals must be in [0, 8], got: " + decimals);
        }
    }
}
