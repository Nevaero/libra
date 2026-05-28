package io.libra.core.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Asset CHF = new Currency("CHF", "Swiss Franc", 2);
    private static final Asset JPY = new Currency("JPY", "Japanese Yen", 0);

    @Test
    void ofScalesDecimalToMinorUnits() {
        assertThat(Money.of(new BigDecimal("12.34"), CHF)).isEqualTo(new Money(1234L, CHF));
        assertThat(Money.of(new BigDecimal("100"), JPY)).isEqualTo(new Money(100L, JPY));
    }

    @Test
    void ofRefusesSilentRounding() {
        // 12.345 has 3 fractional digits but CHF has 2 — RoundingMode.UNNECESSARY must reject it
        // rather than silently round, forcing the caller to round explicitly upstream.
        assertThatThrownBy(() -> Money.of(new BigDecimal("12.345"), CHF))
                .isInstanceOf(ArithmeticException.class);
        // JPY has 0 decimals : any fractional part is a rounding request and must be refused.
        assertThatThrownBy(() -> Money.of(new BigDecimal("100.5"), JPY))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void toDecimalIsTheInverseOfOf() {
        assertThat(new Money(1234L, CHF).toDecimal()).isEqualByComparingTo("12.34");
        assertThat(new Money(100L, JPY).toDecimal()).isEqualByComparingTo("100");
    }

    @Test
    void constructorRejectsNullAsset() {
        assertThatThrownBy(() -> new Money(1L, null)).isInstanceOf(NullPointerException.class);
    }
}
