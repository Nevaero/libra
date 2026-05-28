package io.libra.ledger.repository;

import io.libra.ledger.domain.enums.account.AccountType;
import io.libra.ledger.persistence.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    List<AccountEntity> findAccountsByOwnerId(UUID ownerId);

    // Looks up the *mirror* account on the settlement axis. For an account A identified by
    // (ownerId, asset, type, pending), the mirror is (ownerId, asset, type, !pending).
    // Used at SETTLEMENT time to translate every BOOKING posting on a pending account into
    // a posting on its final account, and vice versa.
    Optional<AccountEntity> findByOwnerIdAndAssetTypeAndAssetCodeAndAssetMicAndTypeAndPending(
            UUID ownerId,
            String assetType,
            String assetCode,
            String assetMic,
            AccountType type,
            boolean pending);
}
