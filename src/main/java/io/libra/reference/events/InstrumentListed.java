package io.libra.reference.events;

import java.time.Instant;
import java.util.UUID;

// providerId only (not the full Provider) — consistent with PriceTick. A consumer that
// needs provider metadata resolves it via the pricing referential.
public record InstrumentListed(UUID instrumentId, Instant listedAt, UUID providerId) {
}
