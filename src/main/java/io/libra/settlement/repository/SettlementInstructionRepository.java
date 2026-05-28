package io.libra.settlement.repository;

import io.libra.settlement.persistence.entity.SettlementInstructionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SettlementInstructionRepository extends JpaRepository<SettlementInstructionEntity, UUID> {
}
