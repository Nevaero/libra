package io.libra.ledger.persistence.mapper;

import io.libra.core.entities.Money;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.mapper.MoneyMapper;
import io.libra.ledger.domain.Posting;
import io.libra.ledger.persistence.entity.PostingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = MoneyMapper.class)
public abstract class PostingMapper {

    @Autowired
    protected MoneyMapper moneyMapper;

    @Mapping(target = "amount", source = "amount", qualifiedByName = "moneyToDomain")
    @Mapping(target = "balanceAfter", source = "balanceAfter", qualifiedByName = "moneyToDomain")
    public abstract Posting toDomain(PostingEntity entity);

    @Mapping(target = "amount", source = "amount", qualifiedByName = "moneyToEntity")
    @Mapping(target = "balanceAfter", source = "balanceAfter", qualifiedByName = "moneyToEntity")
    public abstract PostingEntity toEntity(Posting domain);

    @Named("moneyToDomain")
    protected Money moneyToDomain(MoneyEntity entity) {
        return moneyMapper.toDomain(entity);
    }

    @Named("moneyToEntity")
    protected MoneyEntity moneyToEntity(Money domain) {
        return moneyMapper.toEntity(domain);
    }
}
