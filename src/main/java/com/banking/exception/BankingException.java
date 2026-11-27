package com.banking.exception;

import com.banking.enums.ErrorCategory;
import lombok.Getter;

@Getter
public abstract class BankingException extends RuntimeException {

    private final ErrorCategory category;

    protected BankingException(String message, ErrorCategory category) {
        super(message);
        this.category = category;
    }
}
