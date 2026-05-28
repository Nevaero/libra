package io.libra.ledger.service;

import io.libra.ledger.commands.PostJournalEntryCommand;
import io.libra.ledger.domain.JournalEntry;

import java.util.UUID;

public interface PostingService {

    JournalEntry postJournalEntry(PostJournalEntryCommand cmd);

    JournalEntry postSettlementEntry(UUID bookingEntryId);

}
