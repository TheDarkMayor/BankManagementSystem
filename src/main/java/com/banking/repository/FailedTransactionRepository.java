package com.banking.repository;

import com.banking.entity.FailedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedTransactionRepository extends JpaRepository<FailedTransaction, Long> {}
