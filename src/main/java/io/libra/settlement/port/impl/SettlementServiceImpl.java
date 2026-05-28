package io.libra.settlement.port.impl;

import io.libra.settlement.domain.SettlementBatch;
import io.libra.settlement.domain.SettlementInstruction;
import io.libra.settlement.domain.enums.AssetClass;
import io.libra.settlement.domain.enums.BatchStatus;
import io.libra.settlement.domain.enums.SettlementStatus;
import io.libra.settlement.events.SettlementBatchCompleted;
import io.libra.settlement.internal.BusinessDayCalculator;
import io.libra.settlement.internal.SettlementExecutor;
import io.libra.settlement.persistence.entity.SettlementInstructionEntity;
import io.libra.settlement.persistence.mapper.SettlementBatchMapper;
import io.libra.settlement.persistence.mapper.SettlementInstructionMapper;
import io.libra.settlement.port.SettlementService;
import io.libra.settlement.repository.SettlementBatchRepository;
import io.libra.settlement.repository.SettlementInstructionRepository;
import io.libra.util.Uuids;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private static final int SETTLEMENT_LAG_BUSINESS_DAYS = 2;   // T+2, Libra's core convention

    private final SettlementInstructionRepository instructionRepository;

    private final SettlementInstructionMapper instructionMapper;

    private final SettlementBatchRepository batchRepository;

    private final SettlementBatchMapper batchMapper;

    private final BusinessDayCalculator businessDayCalculator;

    private final SettlementExecutor executor;

    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public SettlementInstruction scheduleSettlement(UUID tradeId, UUID bookingEntryId,
                                                    LocalDate tradeDate, AssetClass assetClass) {
        // Idempotent on the trade (UNIQUE constraint backs this against races).
        Optional<SettlementInstructionEntity> existing = instructionRepository.findByTradeId(tradeId);
        if (existing.isPresent()) {
            return instructionMapper.toDomain(existing.get());
        }

        LocalDate valueDate = businessDayCalculator.addBusinessDays(tradeDate, SETTLEMENT_LAG_BUSINESS_DAYS);
        SettlementInstruction instruction = new SettlementInstruction(
                Uuids.newId(), tradeId, bookingEntryId, assetClass, valueDate,
                SettlementStatus.PENDING, Instant.now(), null, null);
        instructionRepository.save(instructionMapper.toEntity(instruction));
        return instruction;
    }

    // NOT @Transactional : each instruction settles in its own REQUIRES_NEW tx via the executor,
    // so one failure never rolls back the rest of the batch.
    @Override
    public SettlementBatch runDueBatch(LocalDate asOf) {
        List<SettlementInstructionEntity> due = instructionRepository
                .findByStatusAndValueDateLessThanEqual(SettlementStatus.PENDING, asOf);

        Instant runAt = Instant.now();
        long succeeded = 0;
        long failed = 0;
        for (SettlementInstructionEntity instruction : due) {
            try {
                executor.settle(instruction.getId());
                succeeded++;
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                executor.markFailed(instruction.getId(), reason);
                failed++;
            }
        }

        BatchStatus status = failed == 0 ? BatchStatus.COMPLETED : BatchStatus.PARTIAL_FAILURE;
        SettlementBatch batch = new SettlementBatch(
                Uuids.newId(), asOf, runAt, Instant.now(), due.size(), succeeded, failed, status);
        batchRepository.save(batchMapper.toEntity(batch));
        events.publishEvent(new SettlementBatchCompleted(
                batch.id(), asOf, status, succeeded, failed, Instant.now()));
        return batch;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementInstruction> findByTradeId(UUID tradeId) {
        return instructionRepository.findByTradeId(tradeId).map(instructionMapper::toDomain);
    }
}
