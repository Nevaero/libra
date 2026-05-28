package io.libra.ledger.persistence.mapper;

import io.libra.core.entities.Asset;
import io.libra.ledger.domain.Account;
import io.libra.ledger.persistence.entity.AccountEntity;
import io.libra.core.persistence.resolution.AssetRefs;
import io.libra.core.persistence.resolution.AssetResolver;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

// Flattens the typed Asset into (asset_type, asset_code, asset_mic) on write (pure, via
// AssetRefs) and rehydrates it on read via the @Context AssetResolver — pre-populated once
// by the calling service so no per-account lookup hits the DB here.
@Mapper(componentModel = "spring")
public abstract class AccountMapper {

    @Mapping(target = "asset", expression = "java(resolver.resolve(new io.libra.core.persistence.resolution.AssetRef(entity.getAssetType(), entity.getAssetCode(), entity.getAssetMic())))")
    public abstract Account toDomain(AccountEntity entity, @Context AssetResolver resolver);

    @Mapping(target = "assetType", source = "asset", qualifiedByName = "assetType")
    @Mapping(target = "assetCode", source = "asset", qualifiedByName = "assetCode")
    @Mapping(target = "assetMic", source = "asset", qualifiedByName = "assetMic")
    public abstract AccountEntity toEntity(Account domain);

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
}
