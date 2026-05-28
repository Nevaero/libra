package io.libra.core.persistence.mapper;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Money;
import io.libra.core.persistence.entity.MoneyEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Plain Spring component (not a MapStruct interface) — Money mapping requires repository
// lookups via AssetMapper to rehydrate the Asset reference held by the domain record.
// Mappers that embed a Money reference reference this bean via `uses = MoneyMapper.class`.
//
// Note: no @Cacheable here. toDomain is trivial (just delegates to AssetMapper.toDomain
// which IS cached) — caching at the Money level would only add an extra cache layer for
// a wrapper that allocates a tiny record. The Asset cache absorbs the N+1.
@Component
public class MoneyMapper {

    private final AssetMapper assetMapper;

    @Autowired
    public MoneyMapper(AssetMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    public Money toDomain(MoneyEntity entity) {
        if (entity == null) {
            return null;
        }
        Asset asset = assetMapper.toDomain(
                entity.getAssetType(),
                entity.getAssetCode(),
                entity.getAssetMic());
        return new Money(entity.getMinorUnits(), asset);
    }

    public MoneyEntity toEntity(Money domain) {
        if (domain == null) {
            return null;
        }
        return new MoneyEntity(
                domain.minorUnits(),
                assetMapper.typeOf(domain.asset()),
                assetMapper.codeOf(domain.asset()),
                assetMapper.micOf(domain.asset())
        );
    }
}
