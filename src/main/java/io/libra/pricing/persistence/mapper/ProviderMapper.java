package io.libra.pricing.persistence.mapper;

import io.libra.pricing.entities.Provider;
import io.libra.pricing.persistence.entity.ProviderEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProviderMapper {

    Provider toDomain(ProviderEntity entity);

    ProviderEntity toEntity(Provider domain);
}
