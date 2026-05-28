package io.libra.ledger.port.impl;

import io.libra.ledger.commands.OpenAccountCommand;
import io.libra.ledger.commands.PostJournalEntryCommand;
import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.Balance;
import io.libra.ledger.domain.JournalEntry;
import io.libra.ledger.port.LedgerService;
import io.libra.ledger.service.AccountManagementService;
import io.libra.ledger.service.MaintenanceService;
import io.libra.ledger.service.PostingService;
import io.libra.ledger.service.ReadingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final AccountManagementService accountManagementService;

    private final PostingService postingService;

    private final MaintenanceService maintenanceService;

    private final ReadingService readingService;

    // Account aggregate ----------------------------------------------------

    @Override
    public Account openAccount(OpenAccountCommand cmd) {
        return accountManagementService.openAccount(cmd);
    }

    @Override
    public Optional<Account> findAccountById(UUID id) {
        return accountManagementService.findAccountById(id);
    }

    @Override
    public List<Account> findAccountsByOwnerId(UUID ownerId) {
        return accountManagementService.findAccountsByOwnerId(ownerId);
    }

    @Override
    public Optional<Account> findClientAccount(UUID ownerId, io.libra.core.entities.Asset asset) {
        return accountManagementService.findClientAccount(ownerId, asset);
    }

    @Override
    public Account freezeAccount(UUID id, String reason) {
        return accountManagementService.freezeAccount(id, reason);
    }

    @Override
    public Account unfreezeAccount(UUID id, String reason) {
        return accountManagementService.unfreezeAccount(id, reason);
    }

    @Override
    public Account closeAccount(UUID id, String reason) {
        return accountManagementService.closeAccount(id, reason);
    }

    // JournalEntry aggregate -----------------------------------------------

    @Override
    public JournalEntry postJournalEntry(PostJournalEntryCommand cmd) {
        return postingService.postJournalEntry(cmd);
    }

    @Override
    public JournalEntry postSettlementEntry(UUID bookingEntryId) {
        return postingService.postSettlementEntry(bookingEntryId);
    }

    // Balance projection ---------------------------------------------------

    @Override
    public Balance getBalance(UUID accountId) {
        return readingService.getBalance(accountId);
    }

    // Maintenance ----------------------------------------------------------

    @Override
    public void rebuildBalance(UUID accountId) {
        maintenanceService.rebuildBalance(accountId);
    }
}
