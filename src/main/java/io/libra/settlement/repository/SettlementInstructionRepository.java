package io.libra.settlement.repository;

import io.libra.settlement.domain.enums.SettlementStatus;
import io.libra.settlement.persistence.entity.SettlementInstructionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementInstructionRepository extends JpaRepository<SettlementInstructionEntity, UUID> {

    // Idempotency on the trade : a replayed schedule for the same trade returns the same row.
    Optional<SettlementInstructionEntity> findByTradeId(UUID tradeId);

    // The batch picks every instruction whose value date has arrived.
    List<SettlementInstructionEntity> findByStatusAndValueDateLessThanEqual(
            SettlementStatus status, LocalDate valueDate);
}
