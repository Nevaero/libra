package io.libra.ledger.domain;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Currency;
import io.libra.core.entities.Money;
import io.libra.ledger.domain.enums.PostingType;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostingTest {

    private static final Asset CHF = new Currency("CHF", "Swiss Franc", 2);
    private static final Asset USD = new Currency("USD", "US Dollar", 2);

    @Test
    void validPostingConstructs() {
        assertThatCode(() -> new Posting(
                Uuids.newId(), Uuids.newId(), Uuids.newId(), 1L,
                new Money(500L, CHF), new Money(500L, CHF), PostingType.DEBIT))
                .doesNotThrowAnyException();
    }

    @Test
    void negativeAmountIsRejected() {
        // Sign is carried by PostingType, never by the amount itself.
        assertThatThrownBy(() -> new Posting(
                Uuids.newId(), Uuids.newId(), Uuids.newId(), 1L,
                new Money(-1L, CHF), new Money(-1L, CHF), PostingType.DEBIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void amountAndBalanceAfterMustShareAsset() {
        assertThatThrownBy(() -> new Posting(
                Uuids.newId(), Uuids.newId(), Uuids.newId(), 1L,
                new Money(500L, CHF), new Money(500L, USD), PostingType.DEBIT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
