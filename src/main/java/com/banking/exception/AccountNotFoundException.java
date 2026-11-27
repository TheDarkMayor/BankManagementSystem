package com.banking.exception;

import com.banking.enums.ErrorCategory;

public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId, ErrorCategory.ACCOUNT_NOT_FOUND);
    }
}
