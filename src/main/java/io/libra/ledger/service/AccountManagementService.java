package io.libra.ledger.service;

import io.libra.core.entities.Asset;
import io.libra.ledger.commands.OpenAccountCommand;
import io.libra.ledger.domain.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountManagementService {

    Optional<Account> findAccountById(UUID id);

    List<Account> findAccountsByOwnerId(UUID ownerId);

    Account openAccount(OpenAccountCommand cmd);

    // Transition OPEN → FROZEN. Vocabulaire bancaire : un compte *frozen* est gelé suite à une
    // action ciblée (saisie judiciaire, hold compliance, fraude). Distinct de Customer.SUSPENDED
    // qui gèle le client dans son ensemble.
    Account freezeAccount(UUID id, String reason);

    // Transition FROZEN → OPEN. Libère un compte précédemment gelé.
    Account unfreezeAccount(UUID id, String reason);

    // Transition OPEN/FROZEN/PENDING_ACTIVATION → CLOSED. Set closedAt = now. Terminal.
    Account closeAccount(UUID id, String reason);

    // Returns the mirror Account on the settlement axis : same (ownerId, asset, type)
    // but with the opposite `pending` flag. Used by PostingService at settlement time
    // to translate BOOKING postings on pending accounts into SETTLEMENT postings on
    // final accounts (and vice versa for unwind).
    Optional<Account> findMirrorAccount(UUID accountId);

    // Returns the client's final (settled, pending=false) account holding `asset`. The account
    // type is derived from the asset class (Currency → CLIENT_CASH, Security → CLIENT_POSITION),
    // so callers (e.g. validation) need not know about AccountType.
    Optional<Account> findClientAccount(UUID ownerId, Asset asset);
}
