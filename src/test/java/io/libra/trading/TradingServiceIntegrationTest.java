package io.libra.trading;

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
import io.libra.settlement.domain.SettlementInstruction;
import io.libra.settlement.domain.enums.SettlementStatus;
import io.libra.settlement.port.SettlementService;
import io.libra.trading.commands.SubmitOrderCommand;
import io.libra.trading.domain.Order;
import io.libra.trading.domain.enums.OrderStatus;
import io.libra.trading.domain.enums.OrderType;
import io.libra.trading.persistence.entity.TradeEntity;
import io.libra.trading.port.TradingService;
import io.libra.trading.repository.TradeRepository;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// End-to-end convergence : submitOrder drives validation → execution → Delivery-versus-Payment
// booking → T+2 settlement scheduling, all synchronously, and the settlement batch then moves the
// positions onto the final accounts. Distinct fresh pairs per test keep the shared container isolated.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TradingServiceIntegrationTest {

    private static final UUID LIBRA_SIM = UUID.fromString("0190a000-0001-7000-8000-000000000001");

    @Autowired
    private TradingService tradingService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ReferenceDataService referenceData;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private TradeRepository tradeRepository;

    @Test
    void marketBuyExecutesBooksAndSettlesAtTPlus2() {
        Fixture f = setup("CHF", "GBP", 84_900, 85_000);   // bid 0.849, ask 0.850

        Order order = tradingService.submitOrder(buy(f, Uuids.newId(), OrderType.MARKET, Optional.empty()));
        assertThat(order.status()).isEqualTo(OrderStatus.EXECUTED);

        // A single trade was booked for the order, at the ask.
        List<TradeEntity> trades = tradeRepository.findAll().stream()
                .filter(t -> t.getOrderId().equals(order.id())).toList();
        assertThat(trades).hasSize(1);
        TradeEntity trade = trades.getFirst();
        assertThat(trade.getExecutedPriceMinorUnits()).isEqualTo(85_000);

        // Idempotent : re-submitting the same key returns the same order, no second trade.
        Order replay = tradingService.submitOrder(buy(f, order.idempotencyKey(), OrderType.MARKET, Optional.empty()));
        assertThat(replay.id()).isEqualTo(order.id());

        // A PENDING T+2 instruction was scheduled for the trade.
        Optional<SettlementInstruction> scheduled = settlementService.findByTradeId(trade.getId());
        assertThat(scheduled).get().extracting(SettlementInstruction::status).isEqualTo(SettlementStatus.PENDING);

        // Run the batch far enough ahead to cover T+2, then the positions land on the final accounts :
        // the client received 1000.00 EUR and paid 850.00 AUD (notional = 1000.00 × 0.850).
        settlementService.runDueBatch(LocalDate.now(ZoneOffset.UTC).plusDays(7));
        Money baseFinal = ledgerService.getBalance(
                ledgerService.findClientAccount(f.clientId(), f.pair().baseCurrency()).orElseThrow().id()).bookBalance();
        Money quoteFinal = ledgerService.getBalance(
                ledgerService.findClientAccount(f.clientId(), f.pair().quoteCurrency()).orElseThrow().id()).bookBalance();
        assertThat(baseFinal).isEqualTo(new Money(100_000, f.pair().baseCurrency()));
        assertThat(quoteFinal).isEqualTo(new Money(500_000 - 85_000, f.pair().quoteCurrency()));
    }

    @Test
    void rejectsASuspendedClientWithoutBookingATrade() {
        Fixture f = setup("GBP", "CHF", 84_900, 85_000);
        customerService.suspend(f.clientId(), "compliance hold");

        Order order = tradingService.submitOrder(buy(f, Uuids.newId(), OrderType.MARKET, Optional.empty()));

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(tradeRepository.findAll().stream().anyMatch(t -> t.getOrderId().equals(order.id()))).isFalse();
        assertThat(settlementService.findByTradeId(order.id())).isEmpty();
    }

    @Test
    void cancelsALimitOrderTheMarketNeverCrossed() {
        Fixture f = setup("CHF", "EUR", 84_900, 85_000);   // ask 0.850

        // Limit 0.800 is below the ask : marketable never, so no fill.
        Order order = tradingService.submitOrder(buy(f, Uuids.newId(), OrderType.LIMIT, Optional.of(80_000L)));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(tradeRepository.findAll().stream().anyMatch(t -> t.getOrderId().equals(order.id()))).isFalse();
    }

    private SubmitOrderCommand buy(Fixture f, UUID idempotencyKey, OrderType type, Optional<Long> limit) {
        // BUY 1000.00 units of base.
        return new SubmitOrderCommand(idempotencyKey, f.clientId(), f.pair(), Side.BUY,
                new Money(100_000, f.pair().baseCurrency()), type, limit);
    }

    private Fixture setup(String baseCode, String quoteCode, long bid, long ask) {
        UUID clientId = customerService.onboard(new OnboardCustomerCommand(
                "u-" + UUID.randomUUID() + "@libra.io", "Jane", "Doe", LocalDate.of(1990, 1, 1),
                "CH", ClientCategory.RETAIL, RiskProfile.BALANCED)).id();
        customerService.updateKycLevel(clientId, KycLevel.BASIC);
        customerService.activate(clientId);

        CurrencyPair pair = referenceData.registerCurrencyPair(
                new RegisterCurrencyPairCommand(baseCode, quoteCode, 5, LIBRA_SIM));
        Currency quoteCcy = pair.quoteCurrency();

        // Fund the client's quote-currency cash account with 5000.00.
        Account clientCash = ledgerService.openAccount(new OpenAccountCommand(
                clientId, AccountType.CLIENT_CASH, quoteCcy, false, "client-cash"));
        Account nostro = ledgerService.openAccount(new OpenAccountCommand(
                Uuids.newId(), AccountType.NOSTRO, quoteCcy, false, "nostro"));
        ledgerService.postJournalEntry(new PostJournalEntryCommand(
                EntryType.DEPOSIT, EntryPhase.IMMEDIATE, Instant.now(), "fund", null,
                List.of(new PostingDraft(clientCash.id(), new Money(500_000, quoteCcy), PostingType.CREDIT),
                        new PostingDraft(nostro.id(), new Money(500_000, quoteCcy), PostingType.DEBIT))));

        quoteService.ingestTick(new PriceTick(Uuids.newId(), pair.id(), bid, ask,
                1_000_000, 1_000_000, Instant.now(), Instant.now(), LIBRA_SIM, Tenor.SPOT, 5, 1L, null, null));

        return new Fixture(clientId, pair);
    }

    private record Fixture(UUID clientId, CurrencyPair pair) {
    }
}
