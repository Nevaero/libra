package io.libra.pricing.events.instrument;

import io.libra.pricing.entities.Provider;

import java.time.Instant;
import java.util.UUID;

public record InstrumentListed(UUID instrumentId, Instant listedAt, Provider source ) {
}
