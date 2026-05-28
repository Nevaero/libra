package io.libra.settlement;

import io.libra.TestcontainersConfiguration;
import io.libra.core.entities.Currency;
import io.libra.core.entities.Money;
import io.libra.ledger.commands.OpenAccountCommand;
import io.libra.ledger.commands.PostJournalEntryCommand;
import io.libra.ledger.commands.vo.PostingDraft;
import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.JournalEntry;
import io.libra.ledger.domain.enums.PostingType;
import io.libra.ledger.domain.enums.account.AccountType;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryType;
import io.libra.ledger.port.LedgerService;
import io.libra.settlement.domain.SettlementBatch;
import io.libra.settlement.domain.SettlementInstruction;
import io.libra.settlement.domain.enums.AssetClass;
import io.libra.settlement.domain.enums.BatchStatus;
import io.libra.settlement.domain.enums.SettlementStatus;
import io.libra.settlement.port.SettlementService;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Convergence test : settlement schedules T+2 instructions and its batch drives the ledger's
// SETTLEMENT entry (pending → final). One comprehensive run so the global batch is controlled :
// one settleable instruction + one doomed one (booking id points at a non-BOOKING entry).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SettlementServiceIntegrationTest {

    private static final Currency CHF = new Currency("CHF", "Swiss Franc", 2);

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private LedgerService ledgerService;

    @Test
    void schedulesIdempotentlyThenBatchSettlesDueAndIsolatesFailures() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate tradeDate = today.minusDays(7);   // valueDate = tradeDate + 2 business days ≤ today

        // --- a real BOOKING on pending accounts (settleable) ---
        UUID clientOwner = Uuids.newId();
        UUID cptyOwner = Uuids.newId();
        Account clientPending = open(clientOwner, AccountType.CLIENT_CASH, true);
        Account clientFinal = open(clientOwner, AccountType.CLIENT_CASH, false);
        Account cptyPending = open(cptyOwner, AccountType.FX_COUNTERPARTY, true);
        open(cptyOwner, AccountType.FX_COUNTERPARTY, false);   // mirror final for the counterparty
        JournalEntry booking = ledgerService.postJournalEntry(new PostJournalEntryCommand(
                EntryType.FX_TRADE, EntryPhase.BOOKING, Instant.now(), "booking", null,
                List.of(new PostingDraft(clientPending.id(), chf(50_000), PostingType.DEBIT),
                        new PostingDraft(cptyPending.id(), chf(50_000), PostingType.CREDIT))));

        UUID goodTrade = Uuids.newId();
        SettlementInstruction scheduled = settlementService.scheduleSettlement(
                goodTrade, booking.id(), tradeDate, AssetClass.FX);
        assertThat(scheduled.status()).isEqualTo(SettlementStatus.PENDING);
        assertThat(scheduled.valueDate()).isBeforeOrEqualTo(today);
        // idempotent : re-scheduling the same trade returns the same instruction.
        assertThat(settlementService.scheduleSettlement(goodTrade, booking.id(), tradeDate, AssetClass.FX).id())
                .isEqualTo(scheduled.id());

        // --- a doomed instruction : its booking id points at an IMMEDIATE entry (not BOOKING) ---
        Account nostroA = open(Uuids.newId(), AccountType.NOSTRO, false);
        Account nostroB = open(Uuids.newId(), AccountType.NOSTRO, false);
        JournalEntry immediate = ledgerService.postJournalEntry(new PostJournalEntryCommand(
                EntryType.DEPOSIT, EntryPhase.IMMEDIATE, Instant.now(), "not bookable", null,
                List.of(new PostingDraft(nostroA.id(), chf(100), PostingType.CREDIT),
                        new PostingDraft(nostroB.id(), chf(100), PostingType.DEBIT))));
        UUID badTrade = Uuids.newId();
        settlementService.scheduleSettlement(badTrade, immediate.id(), tradeDate, AssetClass.FX);

        // --- batch ---
        SettlementBatch batch = settlementService.runDueBatch(today);
        assertThat(batch.status()).isEqualTo(BatchStatus.PARTIAL_FAILURE);

        assertThat(settlementService.findByTradeId(goodTrade))
                .get().extracting(SettlementInstruction::status).isEqualTo(SettlementStatus.SETTLED);
        assertThat(settlementService.findByTradeId(badTrade))
                .get().extracting(SettlementInstruction::status).isEqualTo(SettlementStatus.FAILED);

        // The good trade's position has moved to the final account.
        assertThat(ledgerService.getBalance(clientFinal.id()).bookBalance()).isEqualTo(chf(-50_000));
    }

    private Account open(UUID ownerId, AccountType type, boolean pending) {
        return ledgerService.openAccount(new OpenAccountCommand(
                ownerId, type, CHF, pending, type + (pending ? "-pending" : "-final")));
    }

    private Money chf(long minorUnits) {
        return new Money(minorUnits, CHF);
    }
}
