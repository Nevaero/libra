package io.libra.ledger.events;

import io.libra.core.entities.Money;
import io.libra.ledger.domain.enums.PostingType;

import java.util.UUID;

public record PostingSummary(
        UUID accountId,
        PostingType type,
        Money amount
) { }
