package io.libra.validation.rules;

import io.libra.core.entities.Currency;
import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Money;
import io.libra.core.entities.enums.CurrencyPairStatus;
import io.libra.core.entities.enums.Side;
import io.libra.customer.domain.Customer;
import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.CustomerStatus;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;
import io.libra.ledger.domain.Balance;
import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.util.Uuids;
import io.libra.validation.domain.ValidationContext;
import io.libra.validation.domain.ValidationFailureReason;
import io.libra.validation.domain.ValidationRequest;
import io.libra.validation.domain.enums.ValidationFailureCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Pure unit tests of the rule logic over a hand-built ValidationContext — no Spring, no DB.
class ValidationRulesTest {

    private static final Currency EUR = new Currency("EUR", "Euro", 2);
    private static final Currency USD = new Currency("USD", "US Dollar", 2);
    private static final UUID PAIR_ID = Uuids.newId();

    @Test
    void buyApprovedWhenFundsCover() {
        // 1000 EUR @ ask 1.08520 = 1085.20 USD = 108520 minor ; 200000 available covers it.
        ValidationContext ctx = ctx(Side.BUY, money(100_000, EUR), Optional.empty(),
                CustomerStatus.ACTIVE, KycLevel.BASIC, CurrencyPairStatus.ACTIVE,
                balance(USD, 200_000), Optional.of(quote(108_500, 108_520)));
        assertThat(new BalanceCheckRule().validate(ctx)).isEmpty();
    }

    @Test
    void buyRejectedWhenFundsShort() {
        ValidationContext ctx = ctx(Side.BUY, money(100_000, EUR), Optional.empty(),
                CustomerStatus.ACTIVE, KycLevel.BASIC, CurrencyPairStatus.ACTIVE,
                balance(USD, 50_000), Optional.of(quote(108_500, 108_520)));
        assertThat(new BalanceCheckRule().validate(ctx))
                .get().extracting(ValidationFailureReason::code)
                .isEqualTo(ValidationFailureCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void sellChecksBaseQuantityAgainstPosition() {
        // SELL spends the base (EUR) ; exposure = 100000 EUR-minor.
        ValidationContext ok = ctx(Side.SELL, money(100_000, EUR), Optional.empty(),
                CustomerStatus.ACTIVE, KycLevel.BASIC, CurrencyPairStatus.ACTIVE,
                balance(EUR, 100_000), Optional.of(quote(108_500, 108_520)));
        assertThat(new BalanceCheckRule().validate(ok)).isEmpty();

        ValidationContext shortFunds = ctx(Side.SELL, money(100_000, EUR), Optional.empty(),
                CustomerStatus.ACTIVE, KycLevel.BASIC, CurrencyPairStatus.ACTIVE,
                balance(EUR, 50_000), Optional.of(quote(108_500, 108_520)));
        assertThat(new BalanceCheckRule().validate(shortFunds))
                .get().extracting(ValidationFailureReason::code)
                .isEqualTo(ValidationFailureCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void customerMustBeActive() {
        assertThat(new CustomerActiveCheckRule().validate(base(CustomerStatus.ACTIVE, KycLevel.BASIC))).isEmpty();
        assertThat(new CustomerActiveCheckRule().validate(base(CustomerStatus.SUSPENDED, KycLevel.BASIC)))
                .get().extracting(ValidationFailureReason::code)
                .isEqualTo(ValidationFailureCode.CUSTOMER_NOT_ACTIVE);
    }

    @Test
    void kycMustBeCompleted() {
        assertThat(new KycCheckRule().validate(base(CustomerStatus.ACTIVE, KycLevel.BASIC))).isEmpty();
        assertThat(new KycCheckRule().validate(base(CustomerStatus.ACTIVE, KycLevel.NONE)))
                .get().extracting(ValidationFailureReason::code)
                .isEqualTo(ValidationFailureCode.KYC_INSUFFICIENT);
    }

    @Test
    void instrumentMustBeTradable() {
        assertThat(new InstrumentStatusCheckRule().validate(
                base(CustomerStatus.ACTIVE, KycLevel.BASIC, CurrencyPairStatus.ACTIVE))).isEmpty();
        assertThat(new InstrumentStatusCheckRule().validate(
                base(CustomerStatus.ACTIVE, KycLevel.BASIC, CurrencyPairStatus.SUSPENDED)))
                .get().extracting(ValidationFailureReason::code)
                .isEqualTo(ValidationFailureCode.INSTRUMENT_NOT_TRADABLE);
    }

    @Test
    void limitPriceFatFingerRejectedButSaneAndMarketPass() {
        // mid = 108510. A limit 100× above is a fat-finger.
        assertThat(new LimitPriceSanityCheckRule().validate(limitCtx(Optional.of(108_510L * 100))))
                .get().extracting(ValidationFailureReason::code)
                .isEqualTo(ValidationFailureCode.LIMIT_PRICE_OUT_OF_BOUNDS);
        assertThat(new LimitPriceSanityCheckRule().validate(limitCtx(Optional.of(108_510L)))).isEmpty();
        assertThat(new LimitPriceSanityCheckRule().validate(limitCtx(Optional.empty()))).isEmpty();
    }

    // --- helpers ---------------------------------------------------------

    private Money money(long minor, Currency c) {
        return new Money(minor, c);
    }

    private Balance balance(Currency asset, long available) {
        Money amount = new Money(available, asset);
        Money zero = new Money(0, asset);
        return new Balance(Uuids.newId(), asset, amount, amount, zero, zero, null, 0L, Instant.now());
    }

    private LatestQuote quote(long bid, long ask) {
        Instant now = Instant.now();
        return new LatestQuote(Uuids.newId(), PAIR_ID, Tenor.SPOT, bid, ask, 1_000_000, 1_000_000,
                5, now, now, Uuids.newId(), 1L, null, null);
    }

    private Customer customer(CustomerStatus status, KycLevel kyc) {
        return new Customer(Uuids.newId(), "a@b.io", "A", "B", LocalDate.of(1990, 1, 1), "CH",
                status, kyc, RiskProfile.BALANCED, ClientCategory.RETAIL, Instant.now(), null);
    }

    private ValidationContext ctx(Side side, Money qty, Optional<Long> limit, CustomerStatus status,
                                  KycLevel kyc, CurrencyPairStatus instrStatus, Balance balance,
                                  Optional<LatestQuote> quote) {
        CurrencyPair pair = new CurrencyPair(PAIR_ID, EUR, USD, instrStatus, 5);
        ValidationRequest request = new ValidationRequest(Uuids.newId(), Uuids.newId(), pair, side, qty, limit);
        return new ValidationContext(request, customer(status, kyc), balance, quote);
    }

    private ValidationContext base(CustomerStatus status, KycLevel kyc) {
        return base(status, kyc, CurrencyPairStatus.ACTIVE);
    }

    private ValidationContext base(CustomerStatus status, KycLevel kyc, CurrencyPairStatus instrStatus) {
        return ctx(Side.BUY, money(100_000, EUR), Optional.empty(), status, kyc, instrStatus,
                balance(USD, 200_000), Optional.of(quote(108_500, 108_520)));
    }

    private ValidationContext limitCtx(Optional<Long> limit) {
        return ctx(Side.BUY, money(100_000, EUR), limit, CustomerStatus.ACTIVE, KycLevel.BASIC,
                CurrencyPairStatus.ACTIVE, balance(USD, 999_999_999), Optional.of(quote(108_500, 108_520)));
    }
}
