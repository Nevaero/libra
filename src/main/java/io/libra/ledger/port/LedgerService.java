package io.libra.ledger.port;

import io.libra.ledger.commands.OpenAccountCommand;
import io.libra.ledger.commands.PostJournalEntryCommand;
import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.Balance;
import io.libra.ledger.domain.JournalEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Module port for the ledger. Single surface visible from other Modulith modules
// (trading, settlement, customer, api...). Internal collaborators (BalanceProjector,
// AccountManagementService.findMirrorAccount, etc.) are intentionally NOT exposed.
public interface LedgerService {

    // Account aggregate ----------------------------------------------------

    Account openAccount(OpenAccountCommand cmd);

    Optional<Account> findAccountById(UUID id);

    List<Account> findAccountsByOwnerId(UUID ownerId);

    Account freezeAccount(UUID id, String reason);

    Account unfreezeAccount(UUID id, String reason);

    Account closeAccount(UUID id, String reason);

    // JournalEntry aggregate -----------------------------------------------

    JournalEntry postJournalEntry(PostJournalEntryCommand cmd);

    JournalEntry postSettlementEntry(UUID bookingEntryId);

    // Balance projection ---------------------------------------------------

    Balance getBalance(UUID accountId);

    // Maintenance ----------------------------------------------------------

    // Rebuilds the Balance projection from the postings table. Admin-only :
    // intended to be exposed through a dedicated, authenticated admin endpoint
    // (LedgerController), not the public trading flow.
    void rebuildBalance(UUID accountId);
}
