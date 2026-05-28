package io.libra.trading.persistence.mapper;

import io.libra.core.entities.Money;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.mapper.MoneyMapper;
import io.libra.core.persistence.resolution.AssetResolver;
import io.libra.trading.domain.Trade;
import io.libra.trading.persistence.entity.TradeEntity;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class TradeMapper {

    protected MoneyMapper moneyMapper;

    @Autowired
    protected void setMoneyMapper(MoneyMapper moneyMapper) {
        this.moneyMapper = moneyMapper;
    }

    @Mapping(target = "executedQuantity", source = "executedQuantity", qualifiedByName = "moneyToDomain")
    public abstract Trade toDomain(TradeEntity entity, @Context AssetResolver resolver);

    @Mapping(target = "executedQuantity", source = "executedQuantity", qualifiedByName = "moneyToEntity")
    public abstract TradeEntity toEntity(Trade domain);

    @Named("moneyToDomain")
    protected Money moneyToDomain(MoneyEntity entity, @Context AssetResolver resolver) {
        return moneyMapper.toDomain(entity, resolver);
    }

    @Named("moneyToEntity")
    protected MoneyEntity moneyToEntity(Money domain) {
        return moneyMapper.toEntity(domain);
    }
}
