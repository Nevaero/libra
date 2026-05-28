package io.libra.ledger.domain;

import io.libra.core.entities.Asset;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryStatus;
import io.libra.ledger.domain.enums.entry.EntryType;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Domain record carrying the fundamental ledger invariant — enforced at the
// compact constructor and protected from any persistence concern.
//
// Invariant : for every Asset X appearing in `postings`,
//   SUM(amount, type=DEBIT, asset=X) == SUM(amount, type=CREDIT, asset=X)
//
// Any imbalance must throw an IllegalArgumentException before persistence.
public record JournalEntry(
        UUID id,
        long sequenceNumber,
        EntryType entryType,
        EntryPhase phase,
        Instant occurredAt,
        Instant recordedAt,
        String description,
        // For a SETTLEMENT entry, points back to the BOOKING entry being released. Nullable.
        UUID causedBy,
        EntryStatus status,
        List<Posting> postings
) {

    public JournalEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(entryType, "entryType must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        postings = postings == null ? List.of() : List.copyOf(postings);

        if (postings.isEmpty()) {
            throw new IllegalArgumentException("JournalEntry must carry at least one posting");
        }

        // Double-entry invariant : per-asset DEBIT total == per-asset CREDIT total.
        Map<Asset, Long> debits = new HashMap<>();
        Map<Asset, Long> credits = new HashMap<>();
        for (Posting p : postings) {
            Asset a = p.amount().asset();
            long m = p.amount().minorUnits();
            switch (p.type()) {
                case DEBIT -> debits.merge(a, m, Math::addExact);
                case CREDIT -> credits.merge(a, m, Math::addExact);
            }
        }
        for (Asset a : debits.keySet()) {
            long d = debits.getOrDefault(a, 0L);
            long c = credits.getOrDefault(a, 0L);
            if (d != c) {
                throw new IllegalArgumentException(
                        "Double-entry invariant violated for asset " + a
                                + " : DEBIT total=" + d + " CREDIT total=" + c);
            }
        }
        for (Asset a : credits.keySet()) {
            long d = debits.getOrDefault(a, 0L);
            long c = credits.getOrDefault(a, 0L);
            if (d != c) {
                throw new IllegalArgumentException(
                        "Double-entry invariant violated for asset " + a
                                + " : DEBIT total=" + d + " CREDIT total=" + c);
            }
        }
    }
}
