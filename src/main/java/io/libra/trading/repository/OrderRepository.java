package io.libra.trading.repository;

import io.libra.trading.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    // Backs idempotent submission : the (client_id, idempotency_key) UNIQUE constraint guarantees
    // at most one row, so a replayed submission resolves to the original order.
    Optional<OrderEntity> findByClientIdAndIdempotencyKey(UUID clientId, UUID idempotencyKey);
}
