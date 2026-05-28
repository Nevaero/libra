package io.libra.ledger.domain.enums.account;

import lombok.experimental.FieldNameConstants;

@FieldNameConstants
public enum AccountType {
    CLIENT_CASH, CLIENT_POSITION, LIBRA_FEES, LIBRA_CAPITAL, MARKET_COUNTERPARTY, FX_COUNTERPARTY, NOSTRO, SUSPENSE
}
