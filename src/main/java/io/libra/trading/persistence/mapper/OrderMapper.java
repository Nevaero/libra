package io.libra.trading.persistence.mapper;

import io.libra.core.entities.Instrument;
import io.libra.core.entities.Money;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.mapper.InstrumentMapper;
import io.libra.core.persistence.mapper.MoneyMapper;
import io.libra.trading.entities.Order;
import io.libra.trading.persistence.entity.OrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = {InstrumentMapper.class, MoneyMapper.class})
public abstract class OrderMapper {

    @Autowired
    protected InstrumentMapper instrumentMapper;

    @Autowired
    protected MoneyMapper moneyMapper;

    @Mapping(target = "instrument", expression = "java(instrumentMapper.toDomain(entity.getInstrumentType(), entity.getInstrumentId()))")
    @Mapping(target = "quantity", source = "quantity", qualifiedByName = "moneyToDomain")
    public abstract Order toDomain(OrderEntity entity);

    @Mapping(target = "instrumentType", source = "instrument", qualifiedByName = "instrumentType")
    @Mapping(target = "instrumentId", source = "instrument", qualifiedByName = "instrumentId")
    @Mapping(target = "quantity", source = "quantity", qualifiedByName = "moneyToEntity")
    public abstract OrderEntity toEntity(Order domain);

    @Named("instrumentType")
    protected String instrumentType(Instrument instrument) {
        return instrumentMapper.typeOf(instrument);
    }

    @Named("instrumentId")
    protected java.util.UUID instrumentId(Instrument instrument) {
        return instrumentMapper.idOf(instrument);
    }

    @Named("moneyToDomain")
    protected Money moneyToDomain(MoneyEntity entity) {
        return moneyMapper.toDomain(entity);
    }

    @Named("moneyToEntity")
    protected MoneyEntity moneyToEntity(Money domain) {
        return moneyMapper.toEntity(domain);
    }
}
