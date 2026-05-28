package io.libra.core.persistence.mapper;

import io.libra.core.entities.Currency;
import io.libra.core.entities.Security;
import io.libra.core.persistence.entity.CurrencyEntity;
import io.libra.core.persistence.entity.SecurityEntity;
import io.libra.core.repository.CurrencyRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

// Mapper for Security. Resolves the FK `quoteCurrencyCode` into a fully-loaded Currency
// via CurrencyRepository in toDomain, and flattens Currency back to its code in toEntity.
@Mapper(componentModel = "spring", uses = CurrencyMapper.class)
public abstract class SecurityMapper {

    @Autowired
    protected CurrencyRepository currencyRepository;

    @Autowired
    protected CurrencyMapper currencyMapper;

    @Mapping(target = "quoteCurrency", source = "quoteCurrencyCode", qualifiedByName = "resolveCurrency")
    public abstract Security toDomain(SecurityEntity entity);

    @Mapping(target = "quoteCurrencyCode", source = "quoteCurrency.code")
    public abstract SecurityEntity toEntity(Security domain);

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
