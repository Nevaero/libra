package io.libra.reference.events;

import io.libra.core.entities.enums.InstrumentStatus;

import java.time.Instant;
import java.util.UUID;

public record InstrumentStatusChanged(UUID instrumentId, InstrumentStatus oldStatus, InstrumentStatus newStatus, String reason, Instant changedAt) {
}
