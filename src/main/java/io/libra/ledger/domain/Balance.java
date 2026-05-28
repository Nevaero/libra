package io.libra.ledger.domain;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Domain record. Invariant verified in the compact constructor:
//   availableBalance = bookBalance - pendingDebits + pendingCredits
// All four Money values must hold the same Asset.
public record Balance(
        UUID accountId,
        Asset asset,
        Money bookBalance,
        Money availableBalance,
        Money pendingDebits,
        Money pendingCredits,
        // Idempotency : id and sequence number of the last applied posting.
        UUID lastPostingId,
        long lastPostingSequenceNumber,
        Instant updatedAt
) {

    public Balance {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(asset, "asset must not be null");
        Objects.requireNonNull(bookBalance, "bookBalance must not be null");
        Objects.requireNonNull(availableBalance, "availableBalance must not be null");
        Objects.requireNonNull(pendingDebits, "pendingDebits must not be null");
        Objects.requireNonNull(pendingCredits, "pendingCredits must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        // All four Money values must reference the same Asset.
        requireSameAsset(bookBalance, "bookBalance", asset);
        requireSameAsset(availableBalance, "availableBalance", asset);
        requireSameAsset(pendingDebits, "pendingDebits", asset);
        requireSameAsset(pendingCredits, "pendingCredits", asset);

        // Money.minus/plus refuses cross-asset operations — cross-asset coherence is enforced.
        Money computed = bookBalance.minus(pendingDebits).plus(pendingCredits);
        if (computed.minorUnits() != availableBalance.minorUnits()) {
            throw new IllegalArgumentException(
                    "availableBalance (" + availableBalance.minorUnits()
                            + ") must equal bookBalance - pendingDebits + pendingCredits ("
                            + computed.minorUnits() + ")");
        }
    }

    private static void requireSameAsset(Money money, String name, Asset expected) {
        if (!Objects.equals(money.asset(), expected)) {
            throw new IllegalArgumentException(
                    name + ".asset (" + money.asset() + ") must equal Balance.asset (" + expected + ")");
        }
    }
}
