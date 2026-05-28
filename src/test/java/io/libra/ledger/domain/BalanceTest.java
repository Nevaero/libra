package io.libra.ledger.domain;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Currency;
import io.libra.core.entities.Money;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceTest {

    private static final Asset CHF = new Currency("CHF", "Swiss Franc", 2);
    private static final Asset USD = new Currency("USD", "US Dollar", 2);

    @Test
    void availableMustEqualBookMinusPendingDebitsPlusPendingCredits() {
        // 100 book - 30 pending debits + 10 pending credits = 80 available
        assertThatCode(() -> new Balance(
                Uuids.newId(), CHF,
                new Money(100L, CHF),   // book
                new Money(80L, CHF),    // available
                new Money(30L, CHF),    // pending debits
                new Money(10L, CHF),    // pending credits
                null, 0L, Instant.now()))
                .doesNotThrowAnyException();
    }

    @Test
    void inconsistentAvailableIsRejected() {
        assertThatThrownBy(() -> new Balance(
                Uuids.newId(), CHF,
                new Money(100L, CHF),
                new Money(999L, CHF),   // wrong : should be 80
                new Money(30L, CHF),
                new Money(10L, CHF),
                null, 0L, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("availableBalance");
    }

    @Test
    void allComponentsMustShareTheBalanceAsset() {
        assertThatThrownBy(() -> new Balance(
                Uuids.newId(), CHF,
                new Money(0L, USD),     // wrong asset
                new Money(0L, CHF),
                new Money(0L, CHF),
                new Money(0L, CHF),
                null, 0L, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
