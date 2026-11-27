package com.banking.repository;

import com.banking.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, String> {

    boolean existsByTransactionId(String transactionId);

    List<BankTransaction> findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(
            String sourceAccountId, String destinationAccountId);
}
