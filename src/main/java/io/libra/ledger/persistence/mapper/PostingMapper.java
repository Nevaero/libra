package io.libra.ledger.persistence.mapper;

import io.libra.core.entities.Money;
import io.libra.ledger.domain.Posting;
import io.libra.ledger.persistence.entity.PostingEntity;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.mapper.MoneyMapper;
import io.libra.core.persistence.resolution.AssetResolver;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class PostingMapper {

    protected MoneyMapper moneyMapper;

    @Autowired
    protected void setMoneyMapper(MoneyMapper moneyMapper) {
        this.moneyMapper = moneyMapper;
    }

    @Mapping(target = "amount", source = "amount", qualifiedByName = "moneyToDomain")
    @Mapping(target = "balanceAfter", source = "balanceAfter", qualifiedByName = "moneyToDomain")
    public abstract Posting toDomain(PostingEntity entity, @Context AssetResolver resolver);

    @Mapping(target = "amount", source = "amount", qualifiedByName = "moneyToEntity")
    @Mapping(target = "balanceAfter", source = "balanceAfter", qualifiedByName = "moneyToEntity")
    public abstract PostingEntity toEntity(Posting domain);

    @Named("moneyToDomain")
    protected Money moneyToDomain(MoneyEntity entity, @Context AssetResolver resolver) {
        return moneyMapper.toDomain(entity, resolver);
    }

    @Named("moneyToEntity")
    protected MoneyEntity moneyToEntity(Money domain) {
        return moneyMapper.toEntity(domain);
    }
}
