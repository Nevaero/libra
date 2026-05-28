package io.libra.reference.repository;

import io.libra.reference.persistence.entity.InstrumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, UUID> {
}
