package io.libra.ledger;

import io.libra.TestcontainersConfiguration;
import io.libra.core.entities.Asset;
import io.libra.core.entities.Currency;
import io.libra.core.entities.Money;
import io.libra.ledger.commands.OpenAccountCommand;
import io.libra.ledger.commands.PostJournalEntryCommand;
import io.libra.ledger.commands.vo.PostingDraft;
import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.Balance;
import io.libra.ledger.domain.JournalEntry;
import io.libra.ledger.domain.enums.PostingType;
import io.libra.ledger.domain.enums.account.AccountType;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryStatus;
import io.libra.ledger.domain.enums.entry.EntryType;
import io.libra.ledger.port.LedgerService;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// End-to-end ledger tests against a real PostgreSQL 18 (Testcontainers) : exercises the
// whole stack — domain invariants, MapStruct mappers, JPA persistence, pessimistic locking,
// the BalanceProjector and the two-phase T+2 booking/settlement cycle.
//
// No @Transactional on the class : each service call commits for real so the projected
// Balance is read back from a committed state, not from a rolled-back first-level cache.
// Isolation between tests comes from fresh random owner/account ids, not from rollback.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LedgerIntegrationTest {

    private static final Asset CHF = new Currency("CHF", "Swiss Franc", 2);

    @Autowired
    private LedgerService ledger;

    @Test
    void openAccountInitialisesAZeroedBalance() {
        Account account = open(Uuids.newId(), AccountType.CLIENT_CASH, false);

        Balance balance = ledger.getBalance(account.id());

        assertThat(balance.bookBalance()).isEqualTo(chf(0));
        assertThat(balance.availableBalance()).isEqualTo(chf(0));
        assertThat(balance.pendingDebits()).isEqualTo(chf(0));
        assertThat(balance.pendingCredits()).isEqualTo(chf(0));
        assertThat(balance.lastPostingSequenceNumber()).isZero();
    }

    @Test
    void immediateDepositMovesBookAndAvailable() {
        // Ledger-centric sign : a client cash account is a liability for Libra, so a CREDIT
        // increases what the client owns. The matching DEBIT lands on the NOSTRO (bank) account.
        Account client = open(Uuids.newId(), AccountType.CLIENT_CASH, false);
        Account nostro = open(Uuids.newId(), AccountType.NOSTRO, false);

        ledger.postJournalEntry(new PostJournalEntryCommand(
                EntryType.DEPOSIT, EntryPhase.IMMEDIATE, Instant.now(), "deposit 100 CHF", null,
                List.of(
                        new PostingDraft(client.id(), chf(10_000), PostingType.CREDIT),
                        new PostingDraft(nostro.id(), chf(10_000), PostingType.DEBIT))));

        Balance clientBalance = ledger.getBalance(client.id());
        assertThat(clientBalance.bookBalance()).isEqualTo(chf(10_000));
        assertThat(clientBalance.availableBalance()).isEqualTo(chf(10_000));
        assertThat(clientBalance.pendingDebits()).isEqualTo(chf(0));

        assertThat(ledger.getBalance(nostro.id()).bookBalance()).isEqualTo(chf(-10_000));
    }

    @Test
    void bookingAccumulatesPendingThenSettlementMovesToFinalAccounts() {
        // The T+2 differentiator. A trade is booked T+0 on *pending* accounts, then settled
        // T+2, transferring the position onto the *final* (mirror) accounts.
        UUID clientOwner = Uuids.newId();
        UUID cptyOwner = Uuids.newId();

        Account clientPending = open(clientOwner, AccountType.CLIENT_CASH, true);
        Account clientFinal = open(clientOwner, AccountType.CLIENT_CASH, false);
        Account cptyPending = open(cptyOwner, AccountType.FX_COUNTERPARTY, true);
        Account cptyFinal = open(cptyOwner, AccountType.FX_COUNTERPARTY, false);

        // --- T+0 BOOKING : 500 CHF committed on the pending accounts ---
        JournalEntry booking = ledger.postJournalEntry(new PostJournalEntryCommand(
                EntryType.FX_TRADE, EntryPhase.BOOKING, Instant.now(), "booking", null,
                List.of(
                        new PostingDraft(clientPending.id(), chf(50_000), PostingType.DEBIT),
                        new PostingDraft(cptyPending.id(), chf(50_000), PostingType.CREDIT))));

        Balance clientPendingAfterBooking = ledger.getBalance(clientPending.id());
        assertThat(clientPendingAfterBooking.pendingDebits()).isEqualTo(chf(50_000));
        assertThat(clientPendingAfterBooking.bookBalance()).isEqualTo(chf(0));
        assertThat(clientPendingAfterBooking.availableBalance()).isEqualTo(chf(-50_000));

        Balance cptyPendingAfterBooking = ledger.getBalance(cptyPending.id());
        assertThat(cptyPendingAfterBooking.pendingCredits()).isEqualTo(chf(50_000));
        assertThat(cptyPendingAfterBooking.availableBalance()).isEqualTo(chf(50_000));

        // Final accounts untouched at T+0.
        assertThat(ledger.getBalance(clientFinal.id()).bookBalance()).isEqualTo(chf(0));

        // --- T+2 SETTLEMENT : pending → final ---
        JournalEntry settlement = ledger.postSettlementEntry(booking.id());

        assertThat(settlement.phase()).isEqualTo(EntryPhase.SETTLEMENT);
        assertThat(settlement.causedBy()).isEqualTo(booking.id());
        assertThat(settlement.status()).isEqualTo(EntryStatus.POSTED);
        // 2 booking postings → 2 settlement postings each (mirror move + pending unwind).
        assertThat(settlement.postings()).hasSize(4);

        // The settled position now lives on the final accounts.
        assertThat(ledger.getBalance(clientFinal.id()).bookBalance()).isEqualTo(chf(-50_000));
        assertThat(ledger.getBalance(clientFinal.id()).availableBalance()).isEqualTo(chf(-50_000));
        assertThat(ledger.getBalance(cptyFinal.id()).bookBalance()).isEqualTo(chf(50_000));
        assertThat(ledger.getBalance(cptyFinal.id()).availableBalance()).isEqualTo(chf(50_000));

        // Pending accounts retain their booking trackers but net back to available == 0 :
        // the commitment is released, the position has moved on.
        Balance clientPendingAfterSettlement = ledger.getBalance(clientPending.id());
        assertThat(clientPendingAfterSettlement.pendingDebits()).isEqualTo(chf(50_000));
        assertThat(clientPendingAfterSettlement.availableBalance()).isEqualTo(chf(0));

        Balance cptyPendingAfterSettlement = ledger.getBalance(cptyPending.id());
        assertThat(cptyPendingAfterSettlement.pendingCredits()).isEqualTo(chf(50_000));
        assertThat(cptyPendingAfterSettlement.availableBalance()).isEqualTo(chf(0));
    }

    @Test
    void unbalancedEntryIsRejectedAndNothingIsPersisted() {
        Account a = open(Uuids.newId(), AccountType.CLIENT_CASH, false);
        Account b = open(Uuids.newId(), AccountType.NOSTRO, false);

        PostJournalEntryCommand unbalanced = new PostJournalEntryCommand(
                EntryType.DEPOSIT, EntryPhase.IMMEDIATE, Instant.now(), "broken", null,
                List.of(
                        new PostingDraft(a.id(), chf(10_000), PostingType.CREDIT),
                        new PostingDraft(b.id(), chf(9_999), PostingType.DEBIT)));

        assertThatThrownBy(() -> ledger.postJournalEntry(unbalanced))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Double-entry invariant violated");

        // The whole transaction rolled back : both balances are still zero.
        assertThat(ledger.getBalance(a.id()).bookBalance()).isEqualTo(chf(0));
        assertThat(ledger.getBalance(a.id()).lastPostingSequenceNumber()).isZero();
        assertThat(ledger.getBalance(b.id()).bookBalance()).isEqualTo(chf(0));
    }

    @Test
    void rebuildBalanceReproducesTheLiveProjection() {
        Account client = open(Uuids.newId(), AccountType.CLIENT_CASH, false);
        Account nostro = open(Uuids.newId(), AccountType.NOSTRO, false);

        ledger.postJournalEntry(new PostJournalEntryCommand(
                EntryType.DEPOSIT, EntryPhase.IMMEDIATE, Instant.now(), "deposit", null,
                List.of(
                        new PostingDraft(client.id(), chf(7_500), PostingType.CREDIT),
                        new PostingDraft(nostro.id(), chf(7_500), PostingType.DEBIT))));

        Balance live = ledger.getBalance(client.id());

        ledger.rebuildBalance(client.id());
        Balance rebuilt = ledger.getBalance(client.id());

        assertThat(rebuilt.bookBalance()).isEqualTo(live.bookBalance());
        assertThat(rebuilt.availableBalance()).isEqualTo(live.availableBalance());
        assertThat(rebuilt.pendingDebits()).isEqualTo(live.pendingDebits());
        assertThat(rebuilt.pendingCredits()).isEqualTo(live.pendingCredits());
        assertThat(rebuilt.lastPostingSequenceNumber()).isEqualTo(live.lastPostingSequenceNumber());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Account open(UUID ownerId, AccountType type, boolean pending) {
        return ledger.openAccount(new OpenAccountCommand(
                ownerId, type, CHF, pending, type + (pending ? "-pending" : "-final")));
    }

    private Money chf(long minorUnits) {
        return new Money(minorUnits, CHF);
    }
}
