package io.libra.ledger.persistence.mapper;

import io.libra.ledger.domain.JournalEntry;
import io.libra.ledger.persistence.entity.JournalEntryEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = PostingMapper.class)
public interface JournalEntryMapper {

    JournalEntry toDomain(JournalEntryEntity entity);

    JournalEntryEntity toEntity(JournalEntry domain);
}
