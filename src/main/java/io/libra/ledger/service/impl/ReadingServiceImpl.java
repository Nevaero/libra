package io.libra.ledger.service.impl;

import io.libra.ledger.domain.Balance;
import io.libra.ledger.persistence.LedgerRefs;
import io.libra.ledger.persistence.entity.BalanceEntity;
import io.libra.ledger.persistence.mapper.BalanceMapper;
import io.libra.ledger.repository.BalanceRepository;
import io.libra.ledger.service.ReadingService;
import io.libra.core.persistence.resolution.AssetResolver;
import io.libra.core.persistence.resolution.ReferenceResolution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReadingServiceImpl implements ReadingService {

    private final BalanceRepository balanceRepository;

    private final BalanceMapper balanceMapper;

    private final ReferenceResolution referenceResolution;

    @Override
    @Transactional(readOnly = true)
    public Balance getBalance(UUID accountId) {
        BalanceEntity entity = balanceRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Balance not found for accountId " + accountId
                                + " — Account may not exist, or its Balance row was never initialised"));
        AssetResolver resolver = referenceResolution.assetResolverFor(List.of(LedgerRefs.of(entity)));
        return balanceMapper.toDomain(entity, resolver);
    }
}
