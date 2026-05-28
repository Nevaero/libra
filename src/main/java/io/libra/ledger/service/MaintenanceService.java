package io.libra.ledger.service;

import java.util.UUID;

public interface MaintenanceService {
    void rebuildBalance(UUID accountId);
}
