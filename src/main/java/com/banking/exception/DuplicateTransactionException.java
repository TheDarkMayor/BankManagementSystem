package com.banking.exception;

import com.banking.enums.ErrorCategory;

public class DuplicateTransactionException extends BankingException {

    public DuplicateTransactionException(String transactionId) {
        super("Duplicate transaction ID: " + transactionId,
                ErrorCategory.DUPLICATE_TRANSACTION);
    }
}
