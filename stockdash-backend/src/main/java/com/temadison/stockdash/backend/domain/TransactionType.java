package com.temadison.stockdash.backend.domain;

public enum TransactionType {
    BUY,
    SELL,
    CASH_DEPOSIT,
    CASH_WITHDRAWAL,
    DIVIDEND,
    INTEREST,
    CASH_FEE;

    public boolean isSecurityTrade() {
        return this == BUY || this == SELL;
    }

    public boolean isExplicitCashTransaction() {
        return switch (this) {
            case CASH_DEPOSIT, CASH_WITHDRAWAL, DIVIDEND, INTEREST, CASH_FEE -> true;
            default -> false;
        };
    }
}
