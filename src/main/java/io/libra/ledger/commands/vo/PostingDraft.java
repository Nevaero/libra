package io.libra.ledger.commands.vo;

import io.libra.core.entities.Money;
import io.libra.ledger.domain.enums.PostingType;

import java.util.UUID;

public record PostingDraft(
        UUID accountId,
        Money amount,                    // positif, le sens vient de `type`
        PostingType type                 // DEBIT ou CREDIT
) { }