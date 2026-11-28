package com.banking.service;

import com.banking.dto.TransactionRequest;
import com.banking.dto.TransactionResult;
import com.banking.entity.Account;
import com.banking.entity.BankTransaction;
import com.banking.enums.AccountStatus;
import com.banking.enums.TransactionStatus;
import com.banking.exception.*;
import com.banking.repository.AccountRepository;
import com.banking.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankingService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final AuditService auditService;

    // ═══════════════════════════════════════════════════════════
    // PUBLIC ENTRY POINT
    // Single @Transactional boundary — all balance mutations are
    // atomic. Helper methods are private to avoid proxy bypass.
    // ═══════════════════════════════════════════════════════════
    @Transactional
    public TransactionResult processTransaction(TransactionRequest request) {
        long startNanos = System.nanoTime();
        String txId = request.transactionId();

        MDC.put("txId", txId);
        MDC.put("txType", request.type().name());

        try {
            // ── Step 1: Input validation ──
            validateRequest(request);

            // ── Step 2: Idempotency check — Tier 1 (application layer) ──
            // Tier 2 is the DB PRIMARY KEY constraint on bank_transaction
            if (transactionRepository.existsByTransactionId(txId)) {
                throw new DuplicateTransactionException(txId);
            }

            // ── Step 3: Route to type-specific handler ──
            switch (request.type()) {
                case DEPOSIT    -> processDeposit(request);
                case WITHDRAWAL -> processWithdrawal(request);
                case TRANSFER   -> processTransfer(request);
            }

            // ── Step 4: Record success audit in separate transaction ──
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            auditService.recordAudit(txId, Thread.currentThread().getName(),
                    durationMs, "SUCCESS");

            log.info("Transaction completed in {}ms", durationMs);
            return new TransactionResult(txId, TransactionStatus.SUCCESS,
                    "Transaction processed", durationMs);

        } catch (BankingException ex) {
            // ── Step 5: Record failure in separate transaction ──
            // Re-throw AFTER recording so @Transactional rolls back balance changes.
            // The failure records survive because AuditService uses REQUIRES_NEW.
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            auditService.recordFailure(request, ex, durationMs);

            log.warn("Transaction failed: {} ({}ms)", ex.getMessage(), durationMs);
            throw ex;

        } finally {
            MDC.clear(); // always clean up — thread pool reuses threads
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DEPOSIT — credit money into a destination account
    // ═══════════════════════════════════════════════════════════
    private void processDeposit(TransactionRequest request) {
        String destId = request.destinationAccountId();

        Account account = accountRepository.findByIdForUpdate(destId)
                .orElseThrow(() -> new AccountNotFoundException(destId));

        validateAccountActive(account);

        account.setBalance(account.getBalance().add(request.amount()));
        accountRepository.save(account);

        saveLedgerEntry(request, TransactionStatus.SUCCESS);

        log.info("DEPOSIT: {} credited ₹{} → balance ₹{}",
                destId, request.amount(), account.getBalance());
    }

    // ═══════════════════════════════════════════════════════════
    // WITHDRAWAL — debit money from a source account
    // ═══════════════════════════════════════════════════════════
    private void processWithdrawal(TransactionRequest request) {
        String srcId = request.sourceAccountId();

        Account account = accountRepository.findByIdForUpdate(srcId)
                .orElseThrow(() -> new AccountNotFoundException(srcId));

        validateAccountActive(account);
        validateSufficientBalance(account, request.amount());

        account.setBalance(account.getBalance().subtract(request.amount()));
        accountRepository.save(account);

        saveLedgerEntry(request, TransactionStatus.SUCCESS);

        log.info("WITHDRAWAL: {} debited ₹{} → balance ₹{}",
                srcId, request.amount(), account.getBalance());
    }

    // ═══════════════════════════════════════════════════════════
    // TRANSFER — debit source, credit destination
    //
    // CRITICAL: ORDERED LOCK ACQUISITION prevents deadlocks.
    //
    // Without ordering:
    //   Thread1 locks ACC-A, waits for ACC-B
    //   Thread2 locks ACC-B, waits for ACC-A → DEADLOCK
    //
    // With ordering (always lock the lexicographically smaller ID first):
    //   Both threads compete for ACC-A first → one waits → NO DEADLOCK
    // ═══════════════════════════════════════════════════════════
    private void processTransfer(TransactionRequest request) {
        String srcId  = request.sourceAccountId();
        String destId = request.destinationAccountId();

        if (srcId.equals(destId)) {
            throw new InvalidTransactionException(
                    "Cannot transfer to the same account",
                    com.banking.enums.ErrorCategory.SELF_TRANSFER);
        }

        // Determine canonical lock order: always lock the smaller ID first
        String firstId  = srcId.compareTo(destId) < 0 ? srcId  : destId;
        String secondId = srcId.compareTo(destId) < 0 ? destId : srcId;

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));
        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        // Map back to source/destination regardless of lock order
        Account source = firstId.equals(srcId) ? first : second;
        Account dest   = firstId.equals(srcId) ? second : first;

        validateAccountActive(source);
        validateAccountActive(dest);
        validateSufficientBalance(source, request.amount());

        source.setBalance(source.getBalance().subtract(request.amount()));
        dest.setBalance(dest.getBalance().add(request.amount()));

        accountRepository.save(source);
        accountRepository.save(dest);

        saveLedgerEntry(request, TransactionStatus.SUCCESS);

        log.info("TRANSFER: {} → {} ₹{} | src balance ₹{} | dest balance ₹{}",
                srcId, destId, request.amount(),
                source.getBalance(), dest.getBalance());
    }

    // ═══════════════════════════════════════════════════════════
    // VALIDATION HELPERS
    // ═══════════════════════════════════════════════════════════
    private void validateRequest(TransactionRequest request) {
        if (request.transactionId() == null || request.transactionId().isBlank()) {
            throw new InvalidTransactionException("Transaction ID is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Amount must be positive");
        }
        switch (request.type()) {
            case DEPOSIT -> {
                if (request.destinationAccountId() == null || request.destinationAccountId().isBlank())
                    throw new InvalidTransactionException("Destination account required for DEPOSIT");
            }
            case WITHDRAWAL -> {
                if (request.sourceAccountId() == null || request.sourceAccountId().isBlank())
                    throw new InvalidTransactionException("Source account required for WITHDRAWAL");
            }
            case TRANSFER -> {
                if (request.sourceAccountId() == null || request.sourceAccountId().isBlank()
                        || request.destinationAccountId() == null || request.destinationAccountId().isBlank())
                    throw new InvalidTransactionException("Both source and destination required for TRANSFER");
            }
        }
    }

    private void validateAccountActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(account.getAccountId(), account.getStatus());
        }
    }

    private void validateSufficientBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    account.getAccountId(), account.getBalance(), amount);
        }
    }

    private void saveLedgerEntry(TransactionRequest request, TransactionStatus status) {
        BankTransaction txn = new BankTransaction(
                request.transactionId(),
                request.type(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                status,
                request.channel() != null ? request.channel() : "SYSTEM"
        );
        transactionRepository.save(txn);
    }
}
