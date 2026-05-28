package io.libra.settlement.domain;

import io.libra.settlement.domain.enums.SettlementStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

// Aggregate root of the settlement module. Created when a TradeExecuted is observed.
// Lifecycle : PENDING -> SETTLED | FAILED.
public record SettlementInstruction(
        UUID id,
        UUID tradeId,
        UUID bookingEntryId,
        LocalDate valueDate,
        SettlementStatus status,
        Instant createdAt,
        Instant settledAt,
        String failureReason
) {

    public SettlementInstruction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tradeId, "tradeId must not be null");
        Objects.requireNonNull(bookingEntryId, "bookingEntryId must not be null");
        Objects.requireNonNull(valueDate, "valueDate must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        switch (status) {
            case PENDING -> {
                if (settledAt != null) {
                    throw new IllegalArgumentException("PENDING instruction must have null settledAt");
                }
                if (failureReason != null) {
                    throw new IllegalArgumentException("PENDING instruction must have null failureReason");
                }
            }
            case SETTLED -> {
                if (settledAt == null) {
                    throw new IllegalArgumentException("SETTLED instruction must have non-null settledAt");
                }
                if (failureReason != null) {
                    throw new IllegalArgumentException("SETTLED instruction must have null failureReason");
                }
            }
            case FAILED -> {
                if (settledAt != null) {
                    throw new IllegalArgumentException("FAILED instruction must have null settledAt");
                }
                if (failureReason == null || failureReason.isBlank()) {
                    throw new IllegalArgumentException("FAILED instruction must have non-blank failureReason");
                }
            }
        }
    }
}
