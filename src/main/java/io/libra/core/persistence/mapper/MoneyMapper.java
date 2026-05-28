package io.libra.core.persistence.mapper;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Money;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.core.persistence.resolution.AssetRef;
import io.libra.core.persistence.resolution.AssetRefs;
import io.libra.core.persistence.resolution.AssetResolver;
import org.springframework.stereotype.Component;

// Plain Spring component (not a MapStruct interface). Read direction needs an AssetResolver
// to rehydrate the Asset held by the Money record — but it depends only on the core SPI, so
// it stays in core with no dependency on the reference-data module (no cycle).
//
// Callers pass an AssetResolver that is normally pre-populated (one batch query per aggregate
// load), so resolving a whole entry's worth of Money values costs no extra round-trips.
// Write direction is pure : flattening an Asset to (type, code, mic) needs no IO.
@Component
public class MoneyMapper {

    public Money toDomain(MoneyEntity entity, AssetResolver resolver) {
        if (entity == null) {
            return null;
        }
        Asset asset = resolver.resolve(
                new AssetRef(entity.getAssetType(), entity.getAssetCode(), entity.getAssetMic()));
        return new Money(entity.getMinorUnits(), asset);
    }

    public MoneyEntity toEntity(Money domain) {
        if (domain == null) {
            return null;
        }
        Asset asset = domain.asset();
        return new MoneyEntity(
                domain.minorUnits(),
                AssetRefs.typeOf(asset),
                AssetRefs.codeOf(asset),
                AssetRefs.micOf(asset));
    }
}
