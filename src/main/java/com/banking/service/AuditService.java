package com.banking.service;

import com.banking.dto.TransactionRequest;
import com.banking.entity.BankTransaction;
import com.banking.entity.FailedTransaction;
import com.banking.entity.TransactionAudit;
import com.banking.enums.TransactionStatus;
import com.banking.exception.BankingException;
import com.banking.repository.BankTransactionRepository;
import com.banking.repository.FailedTransactionRepository;
import com.banking.repository.TransactionAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final BankTransactionRepository transactionRepository;
    private final TransactionAuditRepository auditRepository;
    private final FailedTransactionRepository failedTxnRepository;

    /**
     * Saves a success audit record in its OWN database transaction.
     * REQUIRES_NEW suspends the caller's transaction, opens a new one,
     * commits it, then resumes the caller — so this record always persists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(String txId, String threadName,
                            long durationMs, String executionStatus) {
        TransactionAudit audit = TransactionAudit.builder()
                .transactionId(txId)
                .threadName(threadName)
                .durationMs(durationMs)
                .executionStatus(executionStatus)
                .build();
        auditRepository.save(audit);
    }

    /**
     * Records the failed transaction ledger entry, failure details, and audit record
     * in a SEPARATE transaction (REQUIRES_NEW).
     *
     * Why this must be REQUIRES_NEW:
     * The main transaction will ROLLBACK (to undo partial balance changes).
     * Without REQUIRES_NEW, these failure records would be rolled back too,
     * leaving no audit trail for the failed attempt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(TransactionRequest request,
                              BankingException ex, long durationMs) {
        // Persist the transaction record with FAILED status
        BankTransaction txn = new BankTransaction(
                request.transactionId(),
                request.type(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                TransactionStatus.FAILED,
                request.channel() != null ? request.channel() : "SYSTEM"
        );
        transactionRepository.save(txn);

        // Persist the structured failure details
        FailedTransaction failure = FailedTransaction.builder()
                .transactionId(request.transactionId())
                .errorCategory(ex.getCategory())
                .failureReason(ex.getMessage())
                .build();
        failedTxnRepository.save(failure);

        // Persist the audit record for this failed transaction
        recordAudit(request.transactionId(),
                Thread.currentThread().getName(), durationMs, "FAILED");

        log.debug("Failure recorded for txId={} category={}", 
                request.transactionId(), ex.getCategory());
    }
}
