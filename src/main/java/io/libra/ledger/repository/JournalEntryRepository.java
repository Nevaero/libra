package io.libra.ledger.repository;

import io.libra.ledger.persistence.entity.JournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {

    // Reserves the next global sequence_number from the Postgres sequence
    // `journal_entry_sequence_number`. Called once per posted entry, before construction
    // of the JournalEntry domain record.
    @Query(value = "SELECT nextval('journal_entry_sequence_number')", nativeQuery = true)
    long nextSequenceNumber();

    // Returns every JournalEntry containing at least one posting on the given account,
    // ordered by global sequence_number ASC — i.e. in the chronological order they were
    // applied. Used by MaintenanceService to rebuild a Balance projection from scratch.
    @Query("""
            select distinct je
            from JournalEntryEntity je
            join je.postings p
            where p.accountId = :accountId
            order by je.sequenceNumber asc
            """)
    List<JournalEntryEntity> findAllByPostingAccountIdOrderBySequenceNumber(
            @Param("accountId") UUID accountId);
}
