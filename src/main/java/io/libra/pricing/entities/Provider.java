package io.libra.pricing.entities;

import java.util.Objects;
import java.util.UUID;

// Domain record. Persistence handled by ProviderEntity + ProviderMapper.
public record Provider(UUID id, String name, String code) {

    public Provider {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
