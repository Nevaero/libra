package io.libra.ledger.persistence.mapper;

import io.libra.ledger.domain.JournalEntry;
import io.libra.ledger.persistence.entity.JournalEntryEntity;
import io.libra.core.persistence.resolution.AssetResolver;
import org.mapstruct.Context;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = PostingMapper.class)
public interface JournalEntryMapper {

    // @Context resolver is threaded automatically to PostingMapper for asset rehydration.
    JournalEntry toDomain(JournalEntryEntity entity, @Context AssetResolver resolver);

    JournalEntryEntity toEntity(JournalEntry domain);
}
