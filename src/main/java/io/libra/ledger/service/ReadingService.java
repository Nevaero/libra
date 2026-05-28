package io.libra.ledger.service;

import io.libra.ledger.domain.Balance;

import java.util.UUID;

public interface ReadingService {
    Balance getBalance(UUID accountId);
}
