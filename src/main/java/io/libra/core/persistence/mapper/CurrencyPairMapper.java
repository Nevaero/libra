package io.libra.core.persistence.mapper;

import io.libra.core.entities.Currency;
import io.libra.core.entities.CurrencyPair;
import io.libra.core.persistence.entity.CurrencyPairEntity;
import io.libra.core.repository.CurrencyRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

// Resolves base/quote currency FKs into fully-loaded Currency records via CurrencyRepository.
@Mapper(componentModel = "spring", uses = CurrencyMapper.class)
public abstract class CurrencyPairMapper {

    @Autowired
    protected CurrencyRepository currencyRepository;

    @Autowired
    protected CurrencyMapper currencyMapper;

    @Mapping(target = "baseCurrency", source = "baseCurrencyCode", qualifiedByName = "resolveCurrency")
    @Mapping(target = "quoteCurrency", source = "quoteCurrencyCode", qualifiedByName = "resolveCurrency")
    public abstract CurrencyPair toDomain(CurrencyPairEntity entity);

    @Mapping(target = "baseCurrencyCode", source = "baseCurrency.code")
    @Mapping(target = "quoteCurrencyCode", source = "quoteCurrency.code")
    public abstract CurrencyPairEntity toEntity(CurrencyPair domain);

    @Named("resolveCurrency")
    protected Currency resolveCurrency(String code) {
        if (code == null) {
            return null;
        }
        return currencyRepository.findById(code)
                .map(currencyMapper::toDomain)
                .orElseThrow(() -> new IllegalStateException(
                        "Currency not found for code: " + code));
    }
}
