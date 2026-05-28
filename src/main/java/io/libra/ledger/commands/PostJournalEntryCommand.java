package io.libra.ledger.commands;

import io.libra.ledger.commands.vo.PostingDraft;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostJournalEntryCommand(
        EntryType entryType,             // EQUITY_BUY, FX_TRADE, DEPOSIT, DIVIDEND, FEE...
        EntryPhase phase,                // BOOKING | SETTLEMENT | IMMEDIATE
        Instant occurredAt,              // moment du fait métier
        String description,              // narration humaine
        UUID causedBy,                   // référence vers Order, Trade, etc. (audit trail)
        List<PostingDraft> postings      // ≥2 postings, balanced par asset
) { }
