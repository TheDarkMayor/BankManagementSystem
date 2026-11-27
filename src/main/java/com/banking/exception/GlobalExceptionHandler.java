package com.banking.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(
            InsufficientBalanceException ex) {
        log.warn("Transaction rejected: {}", ex.getMessage());
        return ResponseEntity.status(422).body(errorBody(ex));
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(
            DuplicateTransactionException ex) {
        log.warn("Duplicate transaction: {}", ex.getMessage());
        return ResponseEntity.status(409).body(errorBody(ex));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            AccountNotFoundException ex) {
        log.warn("Account not found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(errorBody(ex));
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<Map<String, Object>> handleInactive(
            AccountInactiveException ex) {
        log.warn("Account inactive: {}", ex.getMessage());
        return ResponseEntity.status(422).body(errorBody(ex));
    }

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(
            InvalidTransactionException ex) {
        log.warn("Invalid transaction: {}", ex.getMessage());
        return ResponseEntity.status(400).body(errorBody(ex));
    }

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<Map<String, Object>> handleBankingException(
            BankingException ex) {
        log.error("Banking error: {}", ex.getMessage());
        return ResponseEntity.status(400).body(errorBody(ex));
    }

    // Tier-2 idempotency: DB primary key violation on bank_transaction
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateKey(
            DataIntegrityViolationException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("bank_transaction_pkey")) {
            log.warn("Database-level duplicate rejection: {}", ex.getMessage());
            return ResponseEntity.status(409).body(Map.of(
                    "error", "DUPLICATE_TRANSACTION",
                    "message", "Transaction already processed (DB constraint)"
            ));
        }
        log.error("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(409).body(Map.of(
                "error", "DATA_INTEGRITY_VIOLATION",
                "message", "A data constraint was violated"
        ));
    }

    private Map<String, Object> errorBody(BankingException ex) {
        return Map.of(
                "error", ex.getCategory().name(),
                "message", ex.getMessage()
        );
    }
}
