package io.libra.ledger.domain;

import io.libra.core.entities.Money;
import io.libra.ledger.domain.enums.PostingType;

import java.util.Objects;
import java.util.UUID;

// Domain record. `amount` and `balanceAfter` must share the same Asset
// (a single posting touches exactly one asset).
public record Posting(
        UUID id,
        UUID journalEntryId,
        UUID accountId,
        long sequenceInEntry,
        Money amount,
        Money balanceAfter,
        PostingType type
) {

    public Posting {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (!Objects.equals(amount.asset(), balanceAfter.asset())) {
            throw new IllegalArgumentException(
                    "amount.asset (" + amount.asset() + ") and balanceAfter.asset ("
                            + balanceAfter.asset() + ") must match");
        }
        if (amount.minorUnits() < 0) {
            throw new IllegalArgumentException(
                    "amount must be non-negative, got: " + amount.minorUnits()
                            + " — sign is carried by PostingType (DEBIT|CREDIT)");
        }
    }
}
