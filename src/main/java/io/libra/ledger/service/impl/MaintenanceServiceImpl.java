package io.libra.ledger.service.impl;

import io.libra.ledger.domain.JournalEntry;
import io.libra.ledger.persistence.entity.JournalEntryEntity;
import io.libra.ledger.persistence.mapper.JournalEntryMapper;
import io.libra.ledger.repository.JournalEntryRepository;
import io.libra.ledger.service.MaintenanceService;
import io.libra.ledger.service.internal.BalanceProjector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final JournalEntryRepository journalEntryRepository;

    private final JournalEntryMapper journalEntryMapper;

    private final BalanceProjector balanceProjector;

    // Rebuilds the Balance projection for `accountId` from scratch by replaying every
    // posting that ever touched it, in canonical (sequence_number ASC) order.
    //
    // Source of truth = postings table. The Balance row is overwritten with the
    // recomputed value. Locked PESSIMISTIC_WRITE inside BalanceProjector so concurrent
    // postings on the same account wait for the rebuild to finish.
    //
    // Intended use : nightly reconciliation job, admin endpoint when divergence is
    // detected, or after a Flyway/manual fix to the postings table.
    @Override
    @Transactional
    public void rebuildBalance(UUID accountId) {
        List<JournalEntryEntity> entities =
                journalEntryRepository.findAllByPostingAccountIdOrderBySequenceNumber(accountId);
        List<JournalEntry> entries = entities.stream()
                .map(journalEntryMapper::toDomain)
                .toList();
        balanceProjector.replayInto(accountId, entries);
    }
}
