package io.libra.validation;

import io.libra.TestcontainersConfiguration;
import io.libra.core.entities.Currency;
import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Money;
import io.libra.core.entities.enums.Side;
import io.libra.customer.commands.OnboardCustomerCommand;
import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;
import io.libra.customer.port.CustomerService;
import io.libra.ledger.commands.OpenAccountCommand;
import io.libra.ledger.commands.PostJournalEntryCommand;
import io.libra.ledger.commands.vo.PostingDraft;
import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.enums.PostingType;
import io.libra.ledger.domain.enums.account.AccountType;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryType;
import io.libra.ledger.port.LedgerService;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.events.PriceTick;
import io.libra.pricing.service.QuoteService;
import io.libra.reference.commands.RegisterCurrencyPairCommand;
import io.libra.reference.port.ReferenceDataService;
import io.libra.util.Uuids;
import io.libra.validation.domain.Approved;
import io.libra.validation.domain.Rejected;
import io.libra.validation.domain.ValidationFailureReason;
import io.libra.validation.domain.ValidationRequest;
import io.libra.validation.domain.ValidationResult;
import io.libra.validation.domain.enums.ValidationFailureCode;
import io.libra.validation.port.ValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Convergence test : validation builds its context from real customer + ledger + pricing state
// and runs the rule chain. Distinct fresh pairs per test keep the shared container isolated.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ValidationServiceIntegrationTest {

    private static final UUID LIBRA_SIM = UUID.fromString("0190a000-0001-7000-8000-000000000001");

    @Autowired
    private ValidationService validationService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private ReferenceDataService referenceData;

    @Autowired
    private QuoteService quoteService;

    @Test
    void approvesAFundedActiveClientBuyingATradablePair() {
        Fixture f = setup("EUR", "GBP");

        ValidationResult result = validationService.validate(buyRequest(f));

        assertThat(result).isInstanceOf(Approved.class);
    }

    @Test
    void rejectsASuspendedClientAndCarriesTheReason() {
        Fixture f = setup("USD", "GBP");
        customerService.suspend(f.clientId(), "compliance hold");

        ValidationResult result = validationService.validate(buyRequest(f));

        assertThat(result).isInstanceOfSatisfying(Rejected.class, rejected ->
                assertThat(rejected.reasons()).extracting(ValidationFailureReason::code)
                        .contains(ValidationFailureCode.CUSTOMER_NOT_ACTIVE));
    }

    private ValidationRequest buyRequest(Fixture f) {
        // BUY 1000 units of base ; exposure ≈ 1000 × ask in the quote currency.
        return new ValidationRequest(Uuids.newId(), f.clientId(), f.pair(), Side.BUY,
                new Money(100_000, f.pair().baseCurrency()), Optional.empty());
    }

    private Fixture setup(String baseCode, String quoteCode) {
        UUID clientId = customerService.onboard(new OnboardCustomerCommand(
                "u-" + UUID.randomUUID() + "@libra.io", "Jane", "Doe", LocalDate.of(1990, 1, 1),
                "CH", ClientCategory.RETAIL, RiskProfile.BALANCED)).id();
        customerService.updateKycLevel(clientId, KycLevel.BASIC);
        customerService.activate(clientId);

        CurrencyPair pair = referenceData.registerCurrencyPair(
                new RegisterCurrencyPairCommand(baseCode, quoteCode, 5, LIBRA_SIM));
        Currency quoteCcy = pair.quoteCurrency();

        // Fund the client's quote-currency cash account.
        Account clientCash = ledgerService.openAccount(new OpenAccountCommand(
                clientId, AccountType.CLIENT_CASH, quoteCcy, false, "client-cash"));
        Account nostro = ledgerService.openAccount(new OpenAccountCommand(
                Uuids.newId(), AccountType.NOSTRO, quoteCcy, false, "nostro"));
        ledgerService.postJournalEntry(new PostJournalEntryCommand(
                EntryType.DEPOSIT, EntryPhase.IMMEDIATE, Instant.now(), "fund", null,
                List.of(new PostingDraft(clientCash.id(), new Money(500_000, quoteCcy), PostingType.CREDIT),
                        new PostingDraft(nostro.id(), new Money(500_000, quoteCcy), PostingType.DEBIT))));

        // A current quote for the pair (~0.851).
        quoteService.ingestTick(new PriceTick(Uuids.newId(), pair.id(), 85_100, 85_123,
                1_000_000, 1_000_000, Instant.now(), Instant.now(), LIBRA_SIM, Tenor.SPOT, 5, 1L, null, null));

        return new Fixture(clientId, pair);
    }

    private record Fixture(UUID clientId, CurrencyPair pair) {
    }
}
