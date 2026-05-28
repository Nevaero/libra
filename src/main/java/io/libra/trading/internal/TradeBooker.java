package io.libra.trading.internal;

import io.libra.core.entities.Asset;
import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Instrument;
import io.libra.core.entities.Money;
import io.libra.core.entities.Security;
import io.libra.core.entities.enums.Side;
import io.libra.ledger.commands.PostJournalEntryCommand;
import io.libra.ledger.commands.vo.PostingDraft;
import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.JournalEntry;
import io.libra.ledger.domain.enums.PostingType;
import io.libra.ledger.domain.enums.entry.EntryPhase;
import io.libra.ledger.domain.enums.entry.EntryType;
import io.libra.ledger.port.LedgerService;
import io.libra.trading.domain.Order;
import io.libra.trading.domain.Trade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Translates an executed Trade into a Delivery-versus-Payment booking entry. A trade moves TWO
// assets, so the entry has two balanced legs : the base leg (the instrument's base asset) and the
// quote leg (the cash notional = quantity × price). Both legs post on PENDING accounts at T+0 ;
// the T+2 settlement batch mirrors them onto the final accounts. Sign convention is ledger-centric
// (client = liability for Libra → CREDIT increases what the client owns).
@Component
@RequiredArgsConstructor
public class TradeBooker {

    private final LedgerService ledgerService;

    public JournalEntry book(Order order, Trade trade, int priceScale) {
        Instrument instrument = order.instrument();
        Asset base = instrument.baseAsset();
        Asset quote = instrument.quoteAsset();

        Money baseQuantity = trade.executedQuantity();
        Money notional = notional(baseQuantity, base, quote, trade.executedPriceMinorUnits(), priceScale);

        // Provision the four pending legs we post on, and ensure their final mirrors exist so the
        // T+2 settlement entry can translate pending → final. resolve* is idempotent.
        Account clientBasePending = ledgerService.resolveClientAccount(order.clientId(), base, true);
        Account clientQuotePending = ledgerService.resolveClientAccount(order.clientId(), quote, true);
        Account cptyBasePending = ledgerService.resolveCounterpartyAccount(base, true);
        Account cptyQuotePending = ledgerService.resolveCounterpartyAccount(quote, true);
        ledgerService.resolveClientAccount(order.clientId(), base, false);
        ledgerService.resolveClientAccount(order.clientId(), quote, false);
        ledgerService.resolveCounterpartyAccount(base, false);
        ledgerService.resolveCounterpartyAccount(quote, false);

        // BUY  : client receives base (CREDIT), pays quote (DEBIT) ; counterparty mirrors.
        // SELL : client gives base (DEBIT), receives quote (CREDIT) ; counterparty mirrors.
        PostingType clientBaseSide = order.side() == Side.BUY ? PostingType.CREDIT : PostingType.DEBIT;
        PostingType clientQuoteSide = order.side() == Side.BUY ? PostingType.DEBIT : PostingType.CREDIT;

        List<PostingDraft> postings = List.of(
                new PostingDraft(clientBasePending.id(), baseQuantity, clientBaseSide),
                new PostingDraft(cptyBasePending.id(), baseQuantity, opposite(clientBaseSide)),
                new PostingDraft(clientQuotePending.id(), notional, clientQuoteSide),
                new PostingDraft(cptyQuotePending.id(), notional, opposite(clientQuoteSide)));

        // caused_by is reserved for the settlement → booking self-link ; a booking entry has none.
        // The order/trade linkage is carried by the description and the Trade.orderId chain.
        return ledgerService.postJournalEntry(new PostJournalEntryCommand(
                entryType(instrument, order.side()),
                EntryPhase.BOOKING,
                trade.executedAt(),
                "trade booking " + trade.id() + " (order " + order.id() + ")",
                null,
                postings));
    }

    // notional_quote = (baseQty / 10^baseDecimals) × (price / 10^priceScale) × 10^quoteDecimals.
    private Money notional(Money baseQuantity, Asset base, Asset quote, long priceMinor, int priceScale) {
        BigDecimal qty = BigDecimal.valueOf(baseQuantity.minorUnits()).movePointLeft(base.decimals());
        BigDecimal price = BigDecimal.valueOf(priceMinor).movePointLeft(priceScale);
        long minorUnits = qty.multiply(price)
                .movePointRight(quote.decimals())
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return new Money(minorUnits, quote);
    }

    private PostingType opposite(PostingType type) {
        return type == PostingType.DEBIT ? PostingType.CREDIT : PostingType.DEBIT;
    }

    private EntryType entryType(Instrument instrument, Side side) {
        return switch (instrument) {
            case CurrencyPair _ -> EntryType.FX_TRADE;
            case Security _ -> side == Side.BUY ? EntryType.EQUITY_BUY : EntryType.EQUITY_SELL;
        };
    }
}
