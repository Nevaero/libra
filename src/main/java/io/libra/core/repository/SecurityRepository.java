package io.libra.core.repository;

import io.libra.core.persistence.entity.SecurityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityRepository extends JpaRepository<SecurityEntity, UUID> {

    // Lookup by the business identity (ticker, mic). The ticker alone is NOT unique:
    // a delisting + relisting recycles the same ticker, and the same ticker can coexist
    // on multiple exchanges. UNIQUE (ticker, mic) is enforced at the DB level.
    Optional<SecurityEntity> findByTickerAndMic(String ticker, String mic);

    Optional<SecurityEntity> findByIsin(String isin);
}
