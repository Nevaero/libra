package io.libra.reference.port;

import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Instrument;
import io.libra.core.entities.Security;
import io.libra.reference.commands.RegisterCurrencyPairCommand;
import io.libra.reference.commands.RegisterSecurityCommand;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Security Master port : the instrument referential's write-side (registration + lifecycle
// state machine) and read-side, exposed to other modules. The resolution SPI (AssetResolver)
// stays separate — this port is for human/admin-driven instrument management, not the
// high-frequency rehydration path.
public interface ReferenceDataService {

    // --- Registration ---------------------------------------------------------------

    Security registerSecurity(RegisterSecurityCommand command);

    CurrencyPair registerCurrencyPair(RegisterCurrencyPairCommand command);

    // --- Security lifecycle (state machine) -----------------------------------------
    // PENDING_LISTING/SUSPENDED/HALTED -> ACTIVE -> {SUSPENDED, HALTED} -> ACTIVE ; * -> DELISTED (terminal).

    Security activateSecurity(UUID id);

    Security suspendSecurity(UUID id, String reason);

    Security haltSecurity(UUID id, String reason);

    Security delistSecurity(UUID id, String reason);

    // --- CurrencyPair lifecycle -----------------------------------------------------
    // ACTIVE <-> SUSPENDED ; * -> DEACTIVATED (terminal).

    CurrencyPair activatePair(UUID id);

    CurrencyPair suspendPair(UUID id, String reason);

    CurrencyPair deactivatePair(UUID id, String reason);

    // --- Read -----------------------------------------------------------------------

    Optional<Instrument> findInstrument(UUID id);

    Optional<Security> findSecurity(UUID id);

    Optional<CurrencyPair> findCurrencyPair(UUID id);

    List<Instrument> listActiveInstruments();
}
