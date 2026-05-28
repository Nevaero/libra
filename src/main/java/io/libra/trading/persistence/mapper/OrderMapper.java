package io.libra.trading.persistence.mapper;

import io.libra.core.entities.Instrument;
import io.libra.core.entities.Money;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.mapper.MoneyMapper;
import io.libra.core.persistence.resolution.AssetResolver;
import io.libra.core.persistence.resolution.InstrumentRefs;
import io.libra.core.persistence.resolution.InstrumentResolver;
import io.libra.trading.domain.Order;
import io.libra.trading.persistence.entity.OrderEntity;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class OrderMapper {

    protected MoneyMapper moneyMapper;

    @Autowired
    protected void setMoneyMapper(MoneyMapper moneyMapper) {
        this.moneyMapper = moneyMapper;
    }

    @Mapping(target = "instrument", expression = "java(instrumentResolver.resolve(new io.libra.core.persistence.resolution.InstrumentRef(entity.getInstrumentType(), entity.getInstrumentId())))")
    @Mapping(target = "quantity", source = "quantity", qualifiedByName = "moneyToDomain")
    public abstract Order toDomain(OrderEntity entity,
                                   @Context InstrumentResolver instrumentResolver,
                                   @Context AssetResolver assetResolver);

    @Mapping(target = "instrumentType", source = "instrument", qualifiedByName = "instrumentType")
    @Mapping(target = "instrumentId", source = "instrument", qualifiedByName = "instrumentId")
    @Mapping(target = "quantity", source = "quantity", qualifiedByName = "moneyToEntity")
    public abstract OrderEntity toEntity(Order domain);

    @Named("instrumentType")
    protected String instrumentType(Instrument instrument) {
        return InstrumentRefs.typeOf(instrument);
    }

    @Named("instrumentId")
    protected UUID instrumentId(Instrument instrument) {
        return InstrumentRefs.idOf(instrument);
    }

    @Named("moneyToDomain")
    protected Money moneyToDomain(MoneyEntity entity, @Context AssetResolver resolver) {
        return moneyMapper.toDomain(entity, resolver);
    }

    @Named("moneyToEntity")
    protected MoneyEntity moneyToEntity(Money domain) {
        return moneyMapper.toEntity(domain);
    }
}
