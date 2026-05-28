package io.libra.settlement.internal;

import io.libra.ledger.port.LedgerService;
import io.libra.settlement.domain.enums.SettlementStatus;
import io.libra.settlement.events.EquityTradeSettled;
import io.libra.settlement.events.FxTradeSettled;
import io.libra.settlement.events.SettlementFailed;
import io.libra.settlement.events.TradeSettled;
import io.libra.settlement.persistence.entity.SettlementInstructionEntity;
import io.libra.settlement.repository.SettlementInstructionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

// Settles one instruction in its OWN transaction (REQUIRES_NEW) so a single failure rolls back
// only that instruction, never the rest of the batch. A separate bean from the service so the
// transactional proxy actually applies (no self-invocation).
@Component
@RequiredArgsConstructor
public class SettlementExecutor {

    private final SettlementInstructionRepository repository;

    private final LedgerService ledgerService;

    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settle(UUID instructionId) {
        SettlementInstructionEntity instruction = load(instructionId);
        // The ledger derives the SETTLEMENT postings (pending → final) from the BOOKING entry.
        ledgerService.postSettlementEntry(instruction.getBookingEntryId());
        instruction.setStatus(SettlementStatus.SETTLED);
        instruction.setSettledAt(Instant.now());
        repository.save(instruction);
        events.publishEvent(toSettledEvent(instruction));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID instructionId, String reason) {
        SettlementInstructionEntity instruction = load(instructionId);
        instruction.setStatus(SettlementStatus.FAILED);
        instruction.setFailureReason(reason);
        repository.save(instruction);
        events.publishEvent(new SettlementFailed(
                instruction.getId(), instruction.getTradeId(), reason, Instant.now()));
    }

    private SettlementInstructionEntity load(UUID instructionId) {
        return repository.findById(instructionId)
                .orElseThrow(() -> new NoSuchElementException("SettlementInstruction not found: " + instructionId));
    }

    private TradeSettled toSettledEvent(SettlementInstructionEntity instruction) {
        Instant now = Instant.now();
        return switch (instruction.getAssetClass()) {
            case FX -> new FxTradeSettled(instruction.getTradeId(), instruction.getId(), now);
            case EQUITY -> new EquityTradeSettled(instruction.getTradeId(), instruction.getId(), now);
        };
    }
}
