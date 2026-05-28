package io.libra.pricing.repository;

import io.libra.pricing.persistence.entity.LatestQuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LatestQuoteRepository extends JpaRepository<LatestQuoteEntity, UUID> {
}
