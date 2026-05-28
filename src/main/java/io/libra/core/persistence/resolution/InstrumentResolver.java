package io.libra.core.persistence.resolution;

import io.libra.core.entities.Instrument;

// SPI : resolves a flat InstrumentRef into a fully-loaded domain Instrument. Same dependency
// inversion as AssetResolver — declared in core, implemented by the reference-data module.
public interface InstrumentResolver {

    Instrument resolve(InstrumentRef ref);
}
