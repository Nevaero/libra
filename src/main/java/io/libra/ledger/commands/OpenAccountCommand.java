package io.libra.ledger.commands;

import io.libra.core.entities.Asset;
import io.libra.ledger.domain.enums.account.AccountType;

import java.util.UUID;

public record OpenAccountCommand(UUID ownerId, AccountType type, Asset asset, boolean pending, String label) { }