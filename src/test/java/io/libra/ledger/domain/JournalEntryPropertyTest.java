package io.libra.ledger.domain;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Currency;
import io.libra.core.entities.Money;
import io.libra.ledger.domain.enums.PostingType;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryStatus;
import io.libra.ledger.domain.enums.entry.EntryType;
import io.libra.util.Uuids;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// THE signature test of the project : the double-entry invariant of JournalEntry, proven
// over thousands of randomly generated entries rather than a handful of examples.
//
// Invariant (compact constructor of JournalEntry) : for every Asset X in the entry,
//   SUM(amount | type=DEBIT, asset=X) == SUM(amount | type=CREDIT, asset=X)
//
// Generation strategy : an entry is built from a list of balanced "legs". Each leg moves a
// positive amount of one asset and emits a DEBIT for the full amount plus one or two CREDITs
// that sum back to it. A leg is therefore balanced by construction for its asset, and any
// combination of legs (multiple assets, asymmetric debit/credit counts) stays balanced —
// which is exactly the space the invariant must accept.
class JournalEntryPropertyTest {

    private static final Asset CHF = new Currency("CHF", "Swiss Franc", 2);
    private static final Asset USD = new Currency("USD", "US Dollar", 2);
    private static final Asset EUR = new Currency("EUR", "Euro", 2);
    private static final Asset JPY = new Currency("JPY", "Japanese Yen", 0);

    // ---------------------------------------------------------------------
    // Properties
    // ---------------------------------------------------------------------

    @Property(tries = 2000)
    void balancedEntriesAreAlwaysAccepted(@ForAll("balancedLegs") List<Leg> legs) {
        List<Posting> postings = toPostings(legs);

        JournalEntry entry = entryOf(postings);

        // Construction did not throw : the invariant held. Re-verify it independently.
        assertPerAssetBalanced(entry);
    }

    @Property(tries = 2000)
    void imbalancedEntriesAreAlwaysRejected(
            @ForAll("balancedLegs") List<Leg> legs,
            @ForAll("assets") Asset strayAsset,
            @ForAll @LongRange(min = 1, max = 1_000_000) long strayAmount,
            @ForAll PostingType straySide) {

        // Start from a balanced set, then inject a single unmatched posting (amount > 0).
        // Whatever side it lands on, it breaks DEBIT==CREDIT for `strayAsset`.
        List<Posting> postings = new ArrayList<>(toPostings(legs));
        postings.add(posting(Uuids.newId(), postings.size() + 1L,
                new Money(strayAmount, strayAsset), straySide));

        assertThatThrownBy(() -> entryOf(postings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Double-entry invariant violated");
    }

    @Property(tries = 1000)
    void acceptanceIsIndependentOfPostingOrder(
            @ForAll("balancedLegs") List<Leg> legs,
            @ForAll long shuffleSeed) {

        List<Posting> postings = new ArrayList<>(toPostings(legs));
        Collections.shuffle(postings, new Random(shuffleSeed));

        // Order has no bearing on a per-asset sum : the entry must still construct.
        assertPerAssetBalanced(entryOf(postings));
    }

    // ---------------------------------------------------------------------
    // Generators
    // ---------------------------------------------------------------------

    @Provide
    Arbitrary<List<Leg>> balancedLegs() {
        Arbitrary<Leg> leg = Arbitraries.of(CHF, USD, EUR, JPY).flatMap(asset ->
                Arbitraries.longs().between(1, 1_000_000).flatMap(amount ->
                        // splitPoint in [0, amount] : 0 or amount → single CREDIT,
                        // strictly between → two CREDITs that sum to amount.
                        Arbitraries.longs().between(0, amount).map(split ->
                                new Leg(asset, amount, split))));
        return leg.list().ofMinSize(1).ofMaxSize(8);
    }

    @Provide
    Arbitrary<Asset> assets() {
        return Arbitraries.of(CHF, USD, EUR, JPY);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private record Leg(Asset asset, long amount, long splitPoint) { }

    private List<Posting> toPostings(List<Leg> legs) {
        List<Posting> postings = new ArrayList<>();
        long seq = 1L;
        for (Leg leg : legs) {
            postings.add(posting(Uuids.newId(), seq++,
                    new Money(leg.amount(), leg.asset()), PostingType.DEBIT));

            boolean splittable = leg.splitPoint() > 0 && leg.splitPoint() < leg.amount();
            if (splittable) {
                postings.add(posting(Uuids.newId(), seq++,
                        new Money(leg.splitPoint(), leg.asset()), PostingType.CREDIT));
                postings.add(posting(Uuids.newId(), seq++,
                        new Money(leg.amount() - leg.splitPoint(), leg.asset()), PostingType.CREDIT));
            } else {
                postings.add(posting(Uuids.newId(), seq++,
                        new Money(leg.amount(), leg.asset()), PostingType.CREDIT));
            }
        }
        return postings;
    }

    private Posting posting(UUID accountId, long sequenceInEntry, Money amount, PostingType type) {
        // balanceAfter is irrelevant to the invariant ; reuse `amount` (same asset, the only
        // constraint Posting enforces between the two).
        return new Posting(Uuids.newId(), null, accountId, sequenceInEntry, amount, amount, type);
    }

    private JournalEntry entryOf(List<Posting> postings) {
        Instant now = Instant.now();
        return new JournalEntry(
                Uuids.newId(),
                1L,
                EntryType.FX_TRADE,
                EntryPhase.IMMEDIATE,
                now,
                now,
                "property-based entry",
                null,
                EntryStatus.POSTED,
                postings);
    }

    private void assertPerAssetBalanced(JournalEntry entry) {
        Map<Asset, Long> debit = new HashMap<>();
        Map<Asset, Long> credit = new HashMap<>();
        for (Posting p : entry.postings()) {
            Map<Asset, Long> side = p.type() == PostingType.DEBIT ? debit : credit;
            side.merge(p.amount().asset(), p.amount().minorUnits(), Long::sum);
        }
        assertThat(debit)
                .as("per-asset DEBIT totals must equal CREDIT totals")
                .isEqualTo(credit);
    }
}
