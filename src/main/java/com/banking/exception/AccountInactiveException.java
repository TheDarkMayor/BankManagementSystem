package com.banking.exception;

import com.banking.enums.AccountStatus;
import com.banking.enums.ErrorCategory;

public class AccountInactiveException extends BankingException {

    public AccountInactiveException(String accountId, AccountStatus status) {
        super(String.format("Account %s is %s", accountId, status),
                ErrorCategory.ACCOUNT_INACTIVE);
    }
}
