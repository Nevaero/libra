package io.libra.validation.port.impl;

import io.libra.core.entities.Asset;
import io.libra.core.entities.enums.Side;
import io.libra.core.persistence.resolution.InstrumentRefs;
import io.libra.customer.domain.Customer;
import io.libra.customer.port.CustomerService;
import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.Balance;
import io.libra.ledger.port.LedgerService;
import io.libra.pricing.domain.LatestQuote;
import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.port.PricingService;
import io.libra.validation.domain.Approved;
import io.libra.validation.domain.Rejected;
import io.libra.validation.domain.ValidationContext;
import io.libra.validation.domain.ValidationFailureReason;
import io.libra.validation.domain.ValidationRequest;
import io.libra.validation.domain.ValidationResult;
import io.libra.validation.events.ValidationFailed;
import io.libra.validation.port.ValidationService;
import io.libra.validation.rules.BalanceCheckRule;
import io.libra.validation.rules.CustomerActiveCheckRule;
import io.libra.validation.rules.InstrumentStatusCheckRule;
import io.libra.validation.rules.KycCheckRule;
import io.libra.validation.rules.LimitPriceSanityCheckRule;
import io.libra.validation.rules.ValidationRule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ValidationServiceImpl implements ValidationService {

    // Collect-all : every rule runs, all failures are reported at once (not fail-fast).
    private static final List<ValidationRule> RULES = List.of(
            new CustomerActiveCheckRule(),
            new KycCheckRule(),
            new InstrumentStatusCheckRule(),
            new BalanceCheckRule(),
            new LimitPriceSanityCheckRule());

    private final CustomerService customerService;

    private final LedgerService ledgerService;

    private final PricingService pricingService;

    private final ApplicationEventPublisher events;

    @Override
    public ValidationResult validate(ValidationRequest request) {
        ValidationContext context = buildContext(request);

        List<ValidationFailureReason> reasons = RULES.stream()
                .map(rule -> rule.validate(context))
                .flatMap(Optional::stream)
                .toList();

        Instant now = Instant.now();
        if (reasons.isEmpty()) {
            return new Approved(request.orderId(), now);
        }
        events.publishEvent(new ValidationFailed(request.orderId(), request.clientId(), reasons, now));
        return new Rejected(request.orderId(), now, reasons);
    }

    // One enriched snapshot built up-front from the three upstream modules, so the rules are pure
    // functions over it (no fetching inside a rule).
    private ValidationContext buildContext(ValidationRequest request) {
        Customer customer = customerService.findById(request.clientId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Customer not found: " + request.clientId()));

        // BUY debits the quote asset (cash), SELL debits the base asset (the held position).
        Asset spentAsset = request.side() == Side.BUY
                ? request.instrument().quoteAsset()
                : request.instrument().baseAsset();
        Account account = ledgerService.findClientAccount(request.clientId(), spentAsset)
                .orElseThrow(() -> new NoSuchElementException(
                        "No client account for owner " + request.clientId()
                                + " in asset " + spentAsset.code()));
        Balance sourceBalance = ledgerService.getBalance(account.id());

        Optional<LatestQuote> latestQuote = pricingService.getLatestQuote(
                InstrumentRefs.idOf(request.instrument()), Tenor.SPOT);

        return new ValidationContext(request, customer, sourceBalance, latestQuote);
    }
}
