package io.libra.core.persistence.resolution;

import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Instrument;
import io.libra.core.entities.Security;

import java.util.UUID;

// Pure write-side flattening of an Instrument into (type, id). No IO.
public final class InstrumentRefs {

    private InstrumentRefs() {
    }

    public static String typeOf(Instrument instrument) {
        if (instrument == null) {
            return null;
        }
        return switch (instrument) {
            case Security s -> InstrumentRef.SECURITY;
            case CurrencyPair cp -> InstrumentRef.CURRENCY_PAIR;
        };
    }

    public static UUID idOf(Instrument instrument) {
        if (instrument == null) {
            return null;
        }
        return switch (instrument) {
            case Security s -> s.id();
            case CurrencyPair cp -> cp.id();
        };
    }

    public static InstrumentRef of(Instrument instrument) {
        return new InstrumentRef(typeOf(instrument), idOf(instrument));
    }
}
