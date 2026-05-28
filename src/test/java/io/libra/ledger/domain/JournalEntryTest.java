package io.libra.ledger.domain;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Currency;
import io.libra.core.entities.Money;
import io.libra.ledger.domain.enums.PostingType;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryStatus;
import io.libra.ledger.domain.enums.entry.EntryType;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

// Example-based companion to JournalEntryPropertyTest : edge cases that are clearer
// expressed as named scenarios than as properties.
class JournalEntryTest {

    private static final Asset CHF = new Currency("CHF", "Swiss Franc", 2);
    private static final Asset USD = new Currency("USD", "US Dollar", 2);

    @Test
    void balancedSingleAssetPairIsAccepted() {
        List<Posting> postings = List.of(
                posting(new Money(1000L, CHF), PostingType.DEBIT),
                posting(new Money(1000L, CHF), PostingType.CREDIT));

        assertThatCode(() -> entryOf(postings)).doesNotThrowAnyException();
    }

    @Test
    void emptyPostingsAreRejected() {
        assertThatThrownBy(() -> entryOf(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one posting");
    }

    @Test
    void nullPostingsAreTreatedAsEmptyAndRejected() {
        assertThatThrownBy(() -> entryOf(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one posting");
    }

    @Test
    void imbalanceInASingleAssetIsRejected() {
        List<Posting> postings = List.of(
                posting(new Money(1000L, CHF), PostingType.DEBIT),
                posting(new Money(999L, CHF), PostingType.CREDIT));

        assertThatThrownBy(() -> entryOf(postings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Double-entry invariant violated");
    }

    @Test
    void balancingIsCheckedPerAssetNotGlobally() {
        // CHF balances (1000 == 1000) but USD does not (500 debit, 0 credit). A naive global
        // count of postings would look fine ; the per-asset check must still reject it.
        List<Posting> postings = List.of(
                posting(new Money(1000L, CHF), PostingType.DEBIT),
                posting(new Money(1000L, CHF), PostingType.CREDIT),
                posting(new Money(500L, USD), PostingType.DEBIT));

        assertThatThrownBy(() -> entryOf(postings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD");
    }

    @Test
    void postingsListIsDefensivelyCopied() {
        List<Posting> mutable = new ArrayList<>(List.of(
                posting(new Money(1000L, CHF), PostingType.DEBIT),
                posting(new Money(1000L, CHF), PostingType.CREDIT)));

        JournalEntry entry = entryOf(mutable);
        mutable.clear();

        assertThat(entry.postings()).hasSize(2);
    }

    private Posting posting(Money amount, PostingType type) {
        return new Posting(Uuids.newId(), null, Uuids.newId(), 1L, amount, amount, type);
    }

    private JournalEntry entryOf(List<Posting> postings) {
        Instant now = Instant.now();
        return new JournalEntry(
                Uuids.newId(), 1L, EntryType.DEPOSIT, EntryPhase.IMMEDIATE,
                now, now, "test", null, EntryStatus.POSTED, postings);
    }
}
