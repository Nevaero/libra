package io.libra.core.entities;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Money is the foundation of every amount in the ledger ; its arithmetic safety
// (fail-fast on overflow, refusal of cross-asset operations) is asserted as properties.
class MoneyPropertyTest {

    private static final Asset CHF = new Currency("CHF", "Swiss Franc", 2);
    private static final Asset USD = new Currency("USD", "US Dollar", 2);

    @Property
    void plusIsCommutative(@ForAll("safe") long a, @ForAll("safe") long b) {
        assertThat(new Money(a, CHF).plus(new Money(b, CHF)))
                .isEqualTo(new Money(b, CHF).plus(new Money(a, CHF)));
    }

    @Property
    void minusUndoesPlus(@ForAll("safe") long a, @ForAll("safe") long b) {
        Money m = new Money(a, CHF);
        Money delta = new Money(b, CHF);
        assertThat(m.plus(delta).minus(delta)).isEqualTo(m);
    }

    @Property
    void plusOverflowFailsFast(@ForAll @LongRange(min = 1, max = 1_000_000) long delta) {
        // Long.MAX_VALUE + (delta >= 1) cannot be represented : Math.addExact must throw.
        assertThatThrownBy(() -> new Money(Long.MAX_VALUE, CHF).plus(new Money(delta, CHF)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Property
    void crossAssetArithmeticFailsFast(@ForAll("safe") long a, @ForAll("safe") long b) {
        Money chf = new Money(a, CHF);
        Money usd = new Money(b, USD);
        assertThatThrownBy(() -> chf.plus(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chf.minus(usd)).isInstanceOf(IllegalArgumentException.class);
    }

    // Range kept well inside long so plus/minus never overflow in the algebraic properties.
    @Provide
    Arbitrary<Long> safe() {
        return Arbitraries.longs().between(-1_000_000_000_000L, 1_000_000_000_000L);
    }
}
