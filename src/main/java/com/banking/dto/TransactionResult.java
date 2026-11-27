package com.banking.dto;

import com.banking.enums.TransactionStatus;

public record TransactionResult(
        String transactionId,
        TransactionStatus status,
        String message,
        long durationMs
) {}
