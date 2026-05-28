package io.libra.pricing.persistence.mapper;

import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.persistence.entity.LatestQuoteEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LatestQuoteMapper {

    LatestQuote toDomain(LatestQuoteEntity entity);

    LatestQuoteEntity toEntity(LatestQuote domain);
}
