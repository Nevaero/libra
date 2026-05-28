package io.libra.settlement.events;

import java.time.Instant;
import java.util.UUID;

// Publié quand une SettlementInstruction passe à FAILED. Consommé par audit + alerting.
// Le `reason` est texte libre — à formaliser en enum si la cardinalité des causes se stabilise.
public record SettlementFailed(
        UUID settlementInstructionId,
        UUID tradeId,
        String reason,
        Instant occurredAt
) {
}
