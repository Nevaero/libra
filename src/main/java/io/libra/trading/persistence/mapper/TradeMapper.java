package io.libra.trading.persistence.mapper;

import io.libra.core.entities.Money;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.mapper.MoneyMapper;
import io.libra.trading.entities.Trade;
import io.libra.trading.persistence.entity.TradeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = MoneyMapper.class)
public abstract class TradeMapper {

    @Autowired
    protected MoneyMapper moneyMapper;

    @Mapping(target = "executedQuantity", source = "executedQuantity", qualifiedByName = "moneyToDomain")
    public abstract Trade toDomain(TradeEntity entity);

    @Mapping(target = "executedQuantity", source = "executedQuantity", qualifiedByName = "moneyToEntity")
    public abstract TradeEntity toEntity(Trade domain);

    @Named("moneyToDomain")
    protected Money moneyToDomain(MoneyEntity entity) {
        return moneyMapper.toDomain(entity);
    }

    @Named("moneyToEntity")
    protected MoneyEntity moneyToEntity(Money domain) {
        return moneyMapper.toEntity(domain);
    }
}
