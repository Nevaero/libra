package io.libra.settlement.persistence.mapper;

import io.libra.settlement.domain.SettlementInstruction;
import io.libra.settlement.persistence.entity.SettlementInstructionEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SettlementInstructionMapper {

    SettlementInstruction toDomain(SettlementInstructionEntity entity);

    SettlementInstructionEntity toEntity(SettlementInstruction domain);
}
