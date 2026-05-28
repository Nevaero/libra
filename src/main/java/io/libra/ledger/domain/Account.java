package io.libra.ledger.domain;

import io.libra.core.entities.Asset;
import io.libra.ledger.domain.enums.account.AccountStatus;
import io.libra.ledger.domain.enums.account.AccountType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Domain record. The Asset reference is the resolved domain reference; persistence
// flattens it into (asset_type, asset_id) via AccountEntity + AccountMapper.
public record Account(
        UUID id,
        UUID ownerId,
        Asset asset,
        AccountStatus status,
        AccountType type,
        // true = pending account (T+0 BOOKING postings), false = final settled account.
        boolean pending,
        String label,
        Instant createdAt,
        Instant closedAt
) {

    public Account {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(asset, "asset must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (status == AccountStatus.CLOSED && closedAt == null) {
            throw new IllegalArgumentException("closedAt must be set when status is CLOSED");
        }
        if (status != AccountStatus.CLOSED && closedAt != null) {
            throw new IllegalArgumentException("closedAt must be null unless status is CLOSED");
        }
    }
}
