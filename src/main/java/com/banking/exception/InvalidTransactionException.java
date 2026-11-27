package com.banking.exception;

import com.banking.enums.ErrorCategory;

public class InvalidTransactionException extends BankingException {

    public InvalidTransactionException(String reason) {
        super(reason, ErrorCategory.INVALID_AMOUNT);
    }

    public InvalidTransactionException(String reason, ErrorCategory category) {
        super(reason, category);
    }
}
