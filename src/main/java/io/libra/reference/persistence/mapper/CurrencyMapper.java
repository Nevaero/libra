package io.libra.reference.persistence.mapper;

import io.libra.core.entities.Currency;
import io.libra.reference.persistence.entity.CurrencyEntity;
import org.mapstruct.Mapper;
import org.springframework.cache.annotation.Cacheable;

// Currency is a static reference dataset (5-10 rows). @Cacheable on toDomain absorbs
// the FK rehydration trafic from Security / Money / Balance mappers.
//
// MapStruct generates a Spring @Component implementation of this interface; Spring AOP
// proxies it and intercepts @Cacheable on the interface method. Key = the CurrencyEntity
// PK (code), guarded by null check.
@Mapper(componentModel = "spring")
public interface CurrencyMapper {

    @Cacheable(value = "currencies", key = "#entity.code", condition = "#entity != null")
    Currency toDomain(CurrencyEntity entity);

    CurrencyEntity toEntity(Currency domain);
}
