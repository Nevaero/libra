package io.libra.settlement.port;

import io.libra.settlement.domain.SettlementBatch;
import io.libra.settlement.domain.SettlementInstruction;
import io.libra.settlement.domain.enums.AssetClass;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

// Settlement port : trading schedules a T+2 instruction synchronously at execution time ; a
// daily batch (the scheduler) settles every due instruction via the ledger.
public interface SettlementService {

    // Creates a PENDING instruction with valueDate = tradeDate + 2 business days. Idempotent on
    // tradeId (a replay returns the existing instruction).
    SettlementInstruction scheduleSettlement(UUID tradeId, UUID bookingEntryId,
                                             LocalDate tradeDate, AssetClass assetClass);

    // Settles every PENDING instruction whose value date has arrived (<= asOf). Each instruction
    // settles in its own transaction ; one failure does not block the others. Returns the batch
    // audit record.
    SettlementBatch runDueBatch(LocalDate asOf);

    Optional<SettlementInstruction> findByTradeId(UUID tradeId);
}
