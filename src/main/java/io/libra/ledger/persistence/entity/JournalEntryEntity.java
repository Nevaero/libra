package io.libra.ledger.persistence.entity;

import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryStatus;
import io.libra.ledger.domain.enums.entry.EntryType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "journal_entries")
public class JournalEntryEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "sequence_number", nullable = false, unique = true)
    private long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntryPhase phase;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "caused_by")
    private UUID causedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntryStatus status;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "journal_entry_id")
    private List<PostingEntity> postings = new ArrayList<>();
}
