package io.libra.ledger.persistence.mapper;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Money;
import io.libra.ledger.domain.Balance;
import io.libra.ledger.persistence.entity.BalanceEntity;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.mapper.MoneyMapper;
import io.libra.core.persistence.resolution.AssetRefs;
import io.libra.core.persistence.resolution.AssetResolver;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class BalanceMapper {

    @Autowired
    protected MoneyMapper moneyMapper;

    @Mapping(target = "asset", expression = "java(resolver.resolve(new io.libra.core.persistence.resolution.AssetRef(entity.getAssetType(), entity.getAssetCode(), entity.getAssetMic())))")
    @Mapping(target = "bookBalance", source = "bookBalance", qualifiedByName = "moneyToDomain")
    @Mapping(target = "availableBalance", source = "availableBalance", qualifiedByName = "moneyToDomain")
    @Mapping(target = "pendingDebits", source = "pendingDebits", qualifiedByName = "moneyToDomain")
    @Mapping(target = "pendingCredits", source = "pendingCredits", qualifiedByName = "moneyToDomain")
    public abstract Balance toDomain(BalanceEntity entity, @Context AssetResolver resolver);

    @Mapping(target = "assetType", source = "asset", qualifiedByName = "assetType")
    @Mapping(target = "assetCode", source = "asset", qualifiedByName = "assetCode")
    @Mapping(target = "assetMic", source = "asset", qualifiedByName = "assetMic")
    @Mapping(target = "bookBalance", source = "bookBalance", qualifiedByName = "moneyToEntity")
    @Mapping(target = "availableBalance", source = "availableBalance", qualifiedByName = "moneyToEntity")
    @Mapping(target = "pendingDebits", source = "pendingDebits", qualifiedByName = "moneyToEntity")
    @Mapping(target = "pendingCredits", source = "pendingCredits", qualifiedByName = "moneyToEntity")
    public abstract BalanceEntity toEntity(Balance domain);

    @Named("assetType")
    protected String assetType(Asset asset) {
        return AssetRefs.typeOf(asset);
    }

    @Named("assetCode")
    protected String assetCode(Asset asset) {
        return AssetRefs.codeOf(asset);
    }

    @Named("assetMic")
    protected String assetMic(Asset asset) {
        return AssetRefs.micOf(asset);
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
