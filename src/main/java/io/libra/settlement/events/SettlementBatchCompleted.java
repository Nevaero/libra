package io.libra.settlement.events;

import io.libra.settlement.entities.enums.BatchStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// Publié à la fin d'un run du scheduler matinal. Consommé par audit / reporting / projections UI.
public record SettlementBatchCompleted(
        UUID batchId,
        LocalDate valueDate,
        BatchStatus finalStatus,
        long instructionsSucceeded,
        long instructionsFailed,
        Instant occurredAt
) {
}
