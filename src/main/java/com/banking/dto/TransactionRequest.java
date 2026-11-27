package com.banking.dto;

import com.banking.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank String transactionId,
        @NotNull  TransactionType type,
        String sourceAccountId,         // nullable — not required for DEPOSIT
        String destinationAccountId,    // nullable — not required for WITHDRAWAL
        @NotNull @Positive BigDecimal amount,
        String channel
) {}
