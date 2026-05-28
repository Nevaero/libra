package io.libra.ledger.events;

import io.libra.ledger.domain.enums.account.AccountType;

import java.time.Instant;
import java.util.UUID;

public record AccountOpened(UUID accountId, AccountType type, boolean pending, UUID ownerId, Instant createdAt) {
}
