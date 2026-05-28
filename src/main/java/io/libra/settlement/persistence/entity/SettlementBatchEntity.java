package io.libra.settlement.persistence.entity;

import io.libra.settlement.entities.enums.BatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "settlement_batches")
public class SettlementBatchEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "instructions_processed", nullable = false)
    private long instructionsProcessed;

    @Column(name = "instructions_succeeded", nullable = false)
    private long instructionsSucceeded;

    @Column(name = "instructions_failed", nullable = false)
    private long instructionsFailed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status;
}
