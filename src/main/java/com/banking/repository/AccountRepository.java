package com.banking.repository;

import com.banking.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    /**
     * Acquires an EXCLUSIVE ROW LOCK via SELECT ... FOR UPDATE.
     * Any concurrent transaction attempting to lock the same row will BLOCK
     * until this transaction commits or rolls back.
     * THIS IS THE PRIMARY CONCURRENCY CONTROL MECHANISM.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :id")
    Optional<Account> findByIdForUpdate(@Param("id") String accountId);

    List<Account> findByCustomer_CustomerId(Long customerId);
}
