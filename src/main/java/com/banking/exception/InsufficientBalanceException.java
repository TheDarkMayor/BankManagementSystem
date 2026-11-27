package com.banking.exception;

import com.banking.enums.ErrorCategory;

import java.math.BigDecimal;

public class InsufficientBalanceException extends BankingException {

    public InsufficientBalanceException(String accountId, BigDecimal balance, BigDecimal amount) {
        super(String.format("Account %s balance ₹%s insufficient for ₹%s",
                accountId, balance.toPlainString(), amount.toPlainString()),
                ErrorCategory.INSUFFICIENT_FUNDS);
    }
}
