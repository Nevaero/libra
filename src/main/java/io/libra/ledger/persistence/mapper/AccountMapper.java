package io.libra.ledger.persistence.mapper;

import io.libra.core.entities.Asset;
import io.libra.core.persistence.mapper.AssetMapper;
import io.libra.ledger.domain.Account;
import io.libra.ledger.persistence.entity.AccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

// AccountMapper flattens the typed Asset into (asset_type, asset_code, asset_mic) on
// write and rehydrates via AssetMapper on read. `asset_mic` is NULL for currency
// accounts, MIC of the listing venue for security accounts.
@Mapper(componentModel = "spring", uses = AssetMapper.class)
public abstract class AccountMapper {

    @Autowired
    protected AssetMapper assetMapper;

    @Mapping(target = "asset", expression = "java(assetMapper.toDomain(entity.getAssetType(), entity.getAssetCode(), entity.getAssetMic()))")
    public abstract Account toDomain(AccountEntity entity);

    @Mapping(target = "assetType", source = "asset", qualifiedByName = "assetType")
    @Mapping(target = "assetCode", source = "asset", qualifiedByName = "assetCode")
    @Mapping(target = "assetMic", source = "asset", qualifiedByName = "assetMic")
    public abstract AccountEntity toEntity(Account domain);

    @Named("assetType")
    protected String assetType(Asset asset) {
        return assetMapper.typeOf(asset);
    }

    @Named("assetCode")
    protected String assetCode(Asset asset) {
        return assetMapper.codeOf(asset);
    }

    @Named("assetMic")
    protected String assetMic(Asset asset) {
        return assetMapper.micOf(asset);
    }
}
