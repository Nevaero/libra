package io.libra.ledger.service.internal;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Money;
import io.libra.core.persistence.resolution.AssetResolver;
import io.libra.core.persistence.resolution.ReferenceResolution;
import io.libra.ledger.domain.Balance;
import io.libra.ledger.domain.JournalEntry;
import io.libra.ledger.domain.Posting;
import io.libra.ledger.domain.enums.PostingType;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.persistence.LedgerRefs;
import io.libra.ledger.persistence.entity.BalanceEntity;
import io.libra.ledger.persistence.mapper.BalanceMapper;
import io.libra.ledger.repository.BalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Internal collaborator owning the Balance aggregate write-side. Kept inside the same
// transactional boundary as the calling service (PostingServiceImpl) so the
// "JournalEntry + Balance projection" pair remains atomic — a non-negotiable invariant
// for a ledger (sufficient-funds checks need a consistent read of the locked balance).
//
// Package-private intentionally : this is an implementation detail of the ledger
// write-side, not a port exposed to other modules.
@Component
@RequiredArgsConstructor
public class BalanceProjector {

    private final BalanceRepository balanceRepository;

    private final BalanceMapper balanceMapper;

    private final ReferenceResolution referenceResolution;

    // Acquires pessimistic locks on every Balance row whose accountId is in the input set.
    // Sorts ids before locking to enforce a global deterministic lock order across
    // concurrent transactions, preventing deadlocks.
    //
    // Invariant : every Account has a Balance row created at openAccount time. A missing
    // row here signals a bug upstream, not a recoverable state — fail fast.
    public Map<UUID, Balance> lockFor(Collection<UUID> accountIds) {
        Set<UUID> sortedIds = new TreeSet<>(accountIds);

        List<BalanceEntity> locked = balanceRepository.findAllByAccountIdInForUpdate(sortedIds);

        if (locked.size() != sortedIds.size()) {
            Set<UUID> found = locked.stream()
                    .map(BalanceEntity::getAccountId)
                    .collect(Collectors.toSet());
            Set<UUID> missing = sortedIds.stream()
                    .filter(id -> !found.contains(id))
                    .collect(Collectors.toSet());
            throw new NoSuchElementException(
                    "Balance rows missing for accounts: " + missing
                            + " — Balance must be initialised at openAccount time");
        }

        AssetResolver resolver = referenceResolution.assetResolverFor(
                locked.stream().map(LedgerRefs::of).toList());
        return locked.stream()
                .map(entity -> balanceMapper.toDomain(entity, resolver))
                .collect(Collectors.toMap(
                        Balance::accountId,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    // Applies the entry's postings to the locked balances and persists the resulting
    // projection. Phase-dependent updates (Libra ledger-centric sign convention :
    // CREDIT increases a liability/client account, DEBIT decreases it) :
    //
    //   - BOOKING            → pendingDebits / pendingCredits accumulate (gross trackers)
    //   - SETTLEMENT / IMMEDIATE → bookBalance moves
    //
    // availableBalance = bookBalance - pendingDebits + pendingCredits, recomputed each time.
    //
    // Entry-level idempotency : if the entry's sequenceNumber is already in the past for
    // a given balance, that balance is skipped — guards against outbox replays.
    public void project(JournalEntry entry, Map<UUID, Balance> locked) {
        Map<UUID, Balance> updated = new LinkedHashMap<>(locked);

        for (Posting posting : entry.postings()) {
            Balance current = updated.get(posting.accountId());
            if (current == null) {
                throw new IllegalStateException(
                        "No locked balance for accountId " + posting.accountId()
                                + " — lockFor should have failed earlier");
            }
            if (entry.sequenceNumber() <= current.lastPostingSequenceNumber()) {
                continue;
            }
            updated.put(posting.accountId(), applyOne(
                    current, posting, entry.phase(), entry.sequenceNumber()));
        }

        balanceRepository.saveAll(updated.values().stream()
                .map(balanceMapper::toEntity)
                .toList());
    }

    // Replays the ordered entries from a zero starting Balance, overwriting the existing
    // row for `accountId`. Used by MaintenanceService for reconciliation when the live
    // projection diverges from the source of truth (postings table).
    //
    // The Balance row is locked PESSIMISTIC_WRITE for the duration of the rebuild to
    // serialise against concurrent postings — the rebuild is therefore safe to run
    // online without quiescing the ledger.
    public void replayInto(UUID accountId, List<JournalEntry> orderedEntries) {
        Map<UUID, Balance> locked = lockFor(List.of(accountId));
        Balance starting = locked.get(accountId);
        Asset asset = starting.asset();

        Money zero = new Money(0L, asset);
        Balance rebuilt = new Balance(
                accountId, asset, zero, zero, zero, zero, null, 0L, Instant.now());

        for (JournalEntry entry : orderedEntries) {
            for (Posting posting : entry.postings()) {
                if (!posting.accountId().equals(accountId)) {
                    continue;
                }
                rebuilt = applyOne(rebuilt, posting, entry.phase(), entry.sequenceNumber());
            }
        }

        balanceRepository.save(balanceMapper.toEntity(rebuilt));
    }

    // Pure per-posting application : returns the updated Balance after applying `posting`
    // under the given phase. Bumps `lastPostingId` and `lastPostingSequenceNumber` so any
    // subsequent replay of the same entry is idempotently skipped.
    private Balance applyOne(Balance current, Posting posting, EntryPhase phase, long entrySequenceNumber) {
        Money amount = posting.amount();
        Money book = current.bookBalance();
        Money pendingDebits = current.pendingDebits();
        Money pendingCredits = current.pendingCredits();

        switch (phase) {
            case BOOKING -> {
                if (posting.type() == PostingType.DEBIT) {
                    pendingDebits = pendingDebits.plus(amount);
                } else {
                    pendingCredits = pendingCredits.plus(amount);
                }
            }
            case SETTLEMENT, IMMEDIATE -> {
                if (posting.type() == PostingType.DEBIT) {
                    book = book.minus(amount);
                } else {
                    book = book.plus(amount);
                }
            }
        }

        Money available = book.minus(pendingDebits).plus(pendingCredits);

        return new Balance(
                current.accountId(),
                current.asset(),
                book,
                available,
                pendingDebits,
                pendingCredits,
                posting.id(),
                entrySequenceNumber,
                Instant.now()
        );
    }
}
