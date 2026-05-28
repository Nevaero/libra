package io.libra.core.entities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

// Domain value object. Pure record, no JPA annotation.
// Persistence is handled by MoneyEntity (@Embeddable) + MoneyMapper.
//
// `Asset` is the typed reference (Currency | Security). Equality uses record equality,
// which for Currency/Security relies on their component equality (UUID for Security, code for Currency).
public record Money(long minorUnits, Asset asset) {

    public Money {
        Objects.requireNonNull(asset, "asset must not be null");
    }

    // Construction from a decimal representation (user input).
    public static Money of(BigDecimal amount, Asset asset) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(asset, "asset must not be null");
        long minor = amount.movePointRight(asset.decimals())
                .setScale(0, RoundingMode.UNNECESSARY)  // refuse les arrondis silencieux
                .longValueExact();
        return new Money(minor, asset);
    }

    // Decimal representation for display purposes.
    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(minorUnits).movePointLeft(asset.decimals());
    }

    // Arithmetic : always in minor units, never in decimal. Fail-fast on overflow.
    public Money plus(Money other) {
        requireSameAsset(other);
        return new Money(Math.addExact(this.minorUnits, other.minorUnits), asset);
    }

    public Money minus(Money other) {
        requireSameAsset(other);
        return new Money(Math.subtractExact(this.minorUnits, other.minorUnits), asset);
    }

    private void requireSameAsset(Money other) {
        if (!Objects.equals(this.asset, other.asset)) {
            throw new IllegalArgumentException(
                    "Cannot operate on different assets: " + this.asset + " vs " + other.asset);
        }
    }
}
