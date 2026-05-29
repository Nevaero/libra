package io.libra.api.dto;

import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.enums.account.AccountStatus;
import io.libra.ledger.domain.enums.account.AccountType;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID ownerId,
        String assetCode,
        AccountStatus status,
        AccountType type,
        boolean pending,
        String label,
        Instant createdAt,
        Instant closedAt
) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.id(), a.ownerId(), a.asset().code(), a.status(), a.type(), a.pending(), a.label(),
                a.createdAt(), a.closedAt());
    }
}
