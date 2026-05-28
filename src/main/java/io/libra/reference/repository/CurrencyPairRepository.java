package io.libra.reference.repository;

import io.libra.core.entities.enums.CurrencyPairStatus;
import io.libra.reference.persistence.entity.CurrencyPairEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CurrencyPairRepository extends JpaRepository<CurrencyPairEntity, UUID> {

    List<CurrencyPairEntity> findByStatus(CurrencyPairStatus status);
}
