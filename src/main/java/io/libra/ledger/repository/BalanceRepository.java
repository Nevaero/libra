package io.libra.ledger.repository;

import io.libra.ledger.persistence.entity.BalanceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BalanceRepository extends JpaRepository<BalanceEntity, UUID> {

    // SELECT ... FOR UPDATE on the rows whose accountId is in the given set, used at
    // posting time to serialise concurrent writes on the same account.
    // The caller MUST sort `accountIds` in a deterministic order (e.g. natural UUID order)
    // to avoid deadlocks between concurrent transactions touching overlapping account sets.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BalanceEntity b where b.accountId in :accountIds")
    List<BalanceEntity> findAllByAccountIdInForUpdate(@Param("accountIds") Collection<UUID> accountIds);
}
