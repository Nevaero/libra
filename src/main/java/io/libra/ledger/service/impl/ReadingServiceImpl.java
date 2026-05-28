package io.libra.ledger.service.impl;

import io.libra.ledger.domain.Balance;
import io.libra.ledger.persistence.mapper.BalanceMapper;
import io.libra.ledger.repository.BalanceRepository;
import io.libra.ledger.service.ReadingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReadingServiceImpl implements ReadingService {

    private final BalanceRepository balanceRepository;

    private final BalanceMapper balanceMapper;

    @Override
    @Transactional(readOnly = true)
    public Balance getBalance(UUID accountId) {
        return balanceRepository.findById(accountId)
                .map(balanceMapper::toDomain)
                .orElseThrow(() -> new NoSuchElementException(
                        "Balance not found for accountId " + accountId
                                + " — Account may not exist, or its Balance row was never initialised"));
    }
}
