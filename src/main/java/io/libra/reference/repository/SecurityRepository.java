package io.libra.reference.repository;

import io.libra.core.entities.enums.SecurityStatus;
import io.libra.reference.persistence.entity.SecurityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityRepository extends JpaRepository<SecurityEntity, UUID> {

    List<SecurityEntity> findByStatus(SecurityStatus status);

    // Lookup by the business identity (ticker, mic). The ticker alone is NOT unique:
    // a delisting + relisting recycles the same ticker, and the same ticker can coexist
    // on multiple exchanges. UNIQUE (ticker, mic) is enforced at the DB level.
    Optional<SecurityEntity> findByTickerAndMic(String ticker, String mic);

    Optional<SecurityEntity> findByIsin(String isin);

    // Standard cross-system identity for a listing : ISIN (ISO 6166, the security) + MIC
    // (ISO 10383, the venue). Used to resolve subscription config → instrumentId at bootstrap.
    Optional<SecurityEntity> findByIsinAndMic(String isin, String mic);

    // Batch fetch for asset resolution : one query for all requested tickers, keyed back to
    // (ticker, mic) in memory. Eliminates the N+1 when rehydrating an aggregate that holds
    // several distinct securities.
    List<SecurityEntity> findByTickerIn(Collection<String> tickers);
}
