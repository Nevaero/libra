package io.libra.settlement.persistence.mapper;

import io.libra.settlement.domain.SettlementBatch;
import io.libra.settlement.persistence.entity.SettlementBatchEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SettlementBatchMapper {

    SettlementBatch toDomain(SettlementBatchEntity entity);

    SettlementBatchEntity toEntity(SettlementBatch domain);
}
