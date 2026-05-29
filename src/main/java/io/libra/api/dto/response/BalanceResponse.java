package io.libra.api.dto.response;

import io.libra.ledger.domain.Balance;

import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        String assetCode,
        long bookBalanceMinorUnits,
        long availableBalanceMinorUnits,
        long pendingDebitsMinorUnits,
        long pendingCreditsMinorUnits,
        Instant updatedAt
) {

    public static BalanceResponse from(Balance b) {
        return new BalanceResponse(
                b.accountId(), b.asset().code(), b.bookBalance().minorUnits(),
                b.availableBalance().minorUnits(), b.pendingDebits().minorUnits(),
                b.pendingCredits().minorUnits(), b.updatedAt());
    }
}
