package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "purchase_requests")
public class PurchaseRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String requestNo;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(nullable = false, length = 50)
    private String applicant;

    private Long applicantUserId;

    @Column(nullable = false, length = 10)
    private String urgency;

    @Column(length = 500)
    private String reason;

    @Column(precision = 14, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(length = 50)
    private String approvedBy;

    private LocalDateTime approvedAt;

    @Column(length = 500)
    private String rejectReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "待审批";
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
