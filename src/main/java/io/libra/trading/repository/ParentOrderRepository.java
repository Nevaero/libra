package io.libra.trading.repository;

import io.libra.trading.persistence.entity.ParentOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ParentOrderRepository extends JpaRepository<ParentOrderEntity, UUID> {
}
