package io.libra.reference.commands;

import io.libra.core.entities.enums.SecurityType;

import java.util.UUID;

// Registers a new tradable security. The instrument goes live (ACTIVE) immediately in Libra
// phase 1 — a PENDING_LISTING pre-registration flow can be layered later if needed.
public record RegisterSecurityCommand(
        String isin,
        String ticker,
        String mic,
        String quoteCurrencyCode,
        String name,
        SecurityType type,
        UUID providerId
) { }
