package io.libra.ledger.service.impl;

import io.libra.core.entities.Money;
import io.libra.core.persistence.resolution.AssetResolver;
import io.libra.core.persistence.resolution.ReferenceResolution;
import io.libra.ledger.commands.PostJournalEntryCommand;
import io.libra.ledger.commands.vo.PostingDraft;
import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.Balance;
import io.libra.ledger.domain.JournalEntry;
import io.libra.ledger.domain.Posting;
import io.libra.ledger.domain.enums.PostingType;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryStatus;
import io.libra.ledger.events.JournalEntryPosted;
import io.libra.ledger.persistence.LedgerRefs;
import io.libra.ledger.persistence.entity.JournalEntryEntity;
import io.libra.ledger.persistence.mapper.JournalEntryMapper;
import io.libra.ledger.repository.JournalEntryRepository;
import io.libra.ledger.service.AccountManagementService;
import io.libra.ledger.service.PostingService;
import io.libra.ledger.service.internal.BalanceProjector;
import io.libra.util.Uuids;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostingServiceImpl implements PostingService {

    private final JournalEntryRepository journalEntryRepository;

    private final JournalEntryMapper journalEntryMapper;

    private final BalanceProjector balanceProjector;

    private final AccountManagementService accountManagementService;

    private final ReferenceResolution referenceResolution;

    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public JournalEntry postJournalEntry(PostJournalEntryCommand cmd) {
        // 1. Lock the balances impacted by the postings (delegated to BalanceProjector).
        Map<UUID, Balance> lockedBalances = balanceProjector.lockFor(
                cmd.postings().stream().map(PostingDraft::accountId).toList());

        // 2. Build the JournalEntry domain record — the compact constructor enforces
        //    the double-entry invariant (per-asset DEBIT total == CREDIT total).
        JournalEntry entry = buildJournalEntry(cmd, lockedBalances);

        // 3. Persist the JournalEntry aggregate.
        journalEntryRepository.save(journalEntryMapper.toEntity(entry));

        // 4. Project the entry onto the Balance aggregate (same TX, atomic with step 3).
        balanceProjector.project(entry, lockedBalances);

        // 5. Publish JournalEntryPosted — Spring Modulith outbox externalises on commit.
        events.publishEvent(new JournalEntryPosted(entry.id()));

        return entry;
    }

    @Override
    @Transactional
    public JournalEntry postSettlementEntry(UUID bookingEntryId) {
        // 1. Load the BOOKING entry being settled.
        JournalEntry booking = loadBookingEntry(bookingEntryId);

        // 2. Derive the SETTLEMENT postings : transfer pending accounts → final accounts.
        PostJournalEntryCommand cmd = buildSettlementCommand(booking);

        // 3. Delegate to the standard posting flow — invariant + balances + event.
        return postJournalEntry(cmd);
    }

    // ---------------------------------------------------------------------
    // Helpers — JournalEntry construction only. Balance lifecycle lives in BalanceProjector.
    // ---------------------------------------------------------------------

    // Builds the full JournalEntry domain record from the command + read-only access to
    // the locked balances (needed to compute Posting.balanceAfter). The compact constructor
    // validates the double-entry invariant.
    //
    // balanceAfter is computed by walking drafts in order : if two drafts hit the same
    // account, the second one sees the running balance updated by the first.
    private JournalEntry buildJournalEntry(PostJournalEntryCommand cmd, Map<UUID, Balance> balances) {
        UUID entryId = Uuids.newId();
        long sequenceNumber = journalEntryRepository.nextSequenceNumber();
        Instant recordedAt = Instant.now();

        Map<UUID, Money> running = new LinkedHashMap<>();
        for (Map.Entry<UUID, Balance> e : balances.entrySet()) {
            running.put(e.getKey(), runningTrackerFor(e.getValue(), cmd.phase()));
        }

        List<Posting> postings = new ArrayList<>(cmd.postings().size());
        long seqInEntry = 1L;
        for (PostingDraft draft : cmd.postings()) {
            Money current = running.get(draft.accountId());
            Money next = applyToTracker(current, draft.amount(), draft.type(), cmd.phase());

            postings.add(new Posting(
                    Uuids.newId(),
                    entryId,
                    draft.accountId(),
                    seqInEntry++,
                    draft.amount(),
                    next,
                    draft.type()
            ));

            running.put(draft.accountId(), next);
        }

        return new JournalEntry(
                entryId,
                sequenceNumber,
                cmd.entryType(),
                cmd.phase(),
                cmd.occurredAt(),
                recordedAt,
                cmd.description(),
                cmd.causedBy(),
                EntryStatus.POSTED,
                postings
        );
    }

    // The "balanceAfter" reported on a Posting reflects the column the posting actually moves :
    //   - BOOKING                : a per-account cumulative tracker (starts at 0)
    //   - SETTLEMENT / IMMEDIATE : bookBalance
    // Keeps the audit trail compact ; the full Balance projection is rebuilt by BalanceProjector.
    private Money runningTrackerFor(Balance b, EntryPhase phase) {
        return switch (phase) {
            case BOOKING -> new Money(0L, b.asset());
            case SETTLEMENT, IMMEDIATE -> b.bookBalance();
        };
    }

    private Money applyToTracker(Money tracker, Money amount, PostingType type, EntryPhase phase) {
        return switch (phase) {
            case BOOKING -> tracker.plus(amount);
            case SETTLEMENT, IMMEDIATE -> type == PostingType.DEBIT
                    ? tracker.minus(amount)
                    : tracker.plus(amount);
        };
    }

    private JournalEntry loadBookingEntry(UUID bookingEntryId) {
        JournalEntryEntity entity = journalEntryRepository.findById(bookingEntryId)
                .orElseThrow(() -> new NoSuchElementException(
                        "JournalEntry not found: " + bookingEntryId));
        if (entity.getPhase() != EntryPhase.BOOKING) {
            throw new IllegalStateException(
                    "Entry " + bookingEntryId + " is in phase " + entity.getPhase()
                            + ", expected BOOKING");
        }
        if (entity.getStatus() != EntryStatus.POSTED) {
            throw new IllegalStateException(
                    "Entry " + bookingEntryId + " is in status " + entity.getStatus()
                            + ", expected POSTED");
        }
        AssetResolver resolver = referenceResolution.assetResolverFor(LedgerRefs.of(entity));
        return journalEntryMapper.toDomain(entity, resolver);
    }

    // For each BOOKING posting on a pending account, emits two SETTLEMENT postings :
    //   - on the mirror final account, SAME DEBIT/CREDIT type as booking → moves the
    //     position to its final destination
    //   - on the pending account itself, OPPOSITE type → unwinds the pending tracker
    //
    // Per-asset the invariant stays balanced : each booking posting contributes one
    // DEBIT and one CREDIT of the same amount (one on pending, one on final), so the
    // double-entry invariant survives the rewrite by construction.
    private PostJournalEntryCommand buildSettlementCommand(JournalEntry booking) {
        List<PostingDraft> drafts = new ArrayList<>(booking.postings().size() * 2);

        for (Posting bookingPosting : booking.postings()) {
            Account mirror = accountManagementService.findMirrorAccount(bookingPosting.accountId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No mirror account for pending account " + bookingPosting.accountId()
                                    + " — settlement requires a (ownerId, asset, type, !pending) twin"));

            // Move position pending → final (same direction).
            drafts.add(new PostingDraft(
                    mirror.id(),
                    bookingPosting.amount(),
                    bookingPosting.type()
            ));

            // Unwind the pending tracker (opposite direction, same account as booking).
            drafts.add(new PostingDraft(
                    bookingPosting.accountId(),
                    bookingPosting.amount(),
                    flip(bookingPosting.type())
            ));
        }

        return new PostJournalEntryCommand(
                booking.entryType(),
                EntryPhase.SETTLEMENT,
                Instant.now(),
                "Settlement of " + booking.id(),
                booking.id(),
                drafts
        );
    }

    private PostingType flip(PostingType type) {
        return type == PostingType.DEBIT ? PostingType.CREDIT : PostingType.DEBIT;
    }
}
