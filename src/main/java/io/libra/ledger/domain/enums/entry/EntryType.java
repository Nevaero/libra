package io.libra.ledger.domain.enums.entry;


import lombok.experimental.FieldNameConstants;

@FieldNameConstants
public enum EntryType {
    DEPOSIT, WITHDRAWAL, FX_TRADE, EQUITY_BUY, EQUITY_SELL, DIVIDEND, FEE, CORPORATE_ACTION
}
