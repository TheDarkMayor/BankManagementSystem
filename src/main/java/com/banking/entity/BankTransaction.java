package com.banking.entity;

import com.banking.enums.TransactionStatus;
import com.banking.enums.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_transaction")
@Getter @Setter @NoArgsConstructor
public class BankTransaction {

    @Id
    @Column(name = "transaction_id", length = 36)
    private String transactionId;   // Client-supplied UUID — NOT @GeneratedValue

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TransactionType type;

    @Column(name = "source_account_id", length = 20)
    private String sourceAccountId;         // NULL for deposits

    @Column(name = "destination_account_id", length = 20)
    private String destinationAccountId;    // NULL for withdrawals

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status;

    @Column(length = 10)
    private String channel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public BankTransaction(String transactionId, TransactionType type,
                           String sourceAccountId, String destinationAccountId,
                           BigDecimal amount, TransactionStatus status, String channel) {
        this.transactionId = transactionId;
        this.type = type;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.status = status;
        this.channel = channel;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
