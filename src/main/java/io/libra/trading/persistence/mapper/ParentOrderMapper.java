package io.libra.trading.persistence.mapper;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Money;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.mapper.MoneyMapper;
import io.libra.core.persistence.resolution.AssetRefs;
import io.libra.core.persistence.resolution.AssetResolver;
import io.libra.trading.domain.ParentOrder;
import io.libra.trading.persistence.entity.ParentOrderEntity;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ParentOrderMapper {

    @Autowired
    protected MoneyMapper moneyMapper;

    @Mapping(target = "sourceAsset", expression = "java(assetResolver.resolve(new io.libra.core.persistence.resolution.AssetRef(entity.getSourceAssetType(), entity.getSourceAssetCode(), entity.getSourceAssetMic())))")
    @Mapping(target = "targetQuantity", source = "targetQuantity", qualifiedByName = "moneyToDomain")
    public abstract ParentOrder toDomain(ParentOrderEntity entity, @Context AssetResolver assetResolver);

    @Mapping(target = "sourceAssetType", source = "sourceAsset", qualifiedByName = "assetType")
    @Mapping(target = "sourceAssetCode", source = "sourceAsset", qualifiedByName = "assetCode")
    @Mapping(target = "sourceAssetMic", source = "sourceAsset", qualifiedByName = "assetMic")
    @Mapping(target = "targetQuantity", source = "targetQuantity", qualifiedByName = "moneyToEntity")
    public abstract ParentOrderEntity toEntity(ParentOrder domain);

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
