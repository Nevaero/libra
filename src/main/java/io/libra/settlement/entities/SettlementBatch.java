package io.libra.settlement.entities;

import io.libra.settlement.entities.enums.BatchStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

// Audit trail for a single morning scheduler run.
public record SettlementBatch(
        UUID id,
        LocalDate valueDate,
        Instant runAt,
        Instant completedAt,
        long instructionsProcessed,
        long instructionsSucceeded,
        long instructionsFailed,
        BatchStatus status
) {

    public SettlementBatch {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(valueDate, "valueDate must not be null");
        Objects.requireNonNull(runAt, "runAt must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (status == BatchStatus.RUNNING && completedAt != null) {
            throw new IllegalArgumentException("RUNNING batch must have null completedAt");
        }
        if (status != BatchStatus.RUNNING && completedAt == null) {
            throw new IllegalArgumentException("Terminal batch (non-RUNNING) must have non-null completedAt");
        }
        if (instructionsProcessed != instructionsSucceeded + instructionsFailed) {
            throw new IllegalArgumentException(
                    "instructionsProcessed (" + instructionsProcessed
                            + ") must equal succeeded (" + instructionsSucceeded
                            + ") + failed (" + instructionsFailed + ")");
        }
    }
}
