package io.libra.ledger.events;

import io.libra.ledger.domain.enums.account.AccountStatus;

import java.util.UUID;

public record AccountStatusChanged(UUID accountId, AccountStatus oldStatus, AccountStatus newStatus, String reason) {
}
