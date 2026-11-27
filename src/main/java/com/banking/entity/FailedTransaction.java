package com.banking.entity;

import com.banking.enums.ErrorCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_transaction")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FailedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "failure_id")
    private Long failureId;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_category", nullable = false, length = 30)
    private ErrorCategory errorCategory;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failed_at", nullable = false, updatable = false)
    private LocalDateTime failedAt;

    @PrePersist
    protected void onCreate() {
        this.failedAt = LocalDateTime.now();
    }
}
