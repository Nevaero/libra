package io.libra.core.repository;

import io.libra.core.persistence.entity.CurrencyPairEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CurrencyPairRepository extends JpaRepository<CurrencyPairEntity, UUID> {
}
