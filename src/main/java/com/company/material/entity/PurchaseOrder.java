package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String orderNo;

    @Column(nullable = false)
    private Long supplierId;

    @Column(length = 100)
    private String supplierName;

    @Column(precision = 14, scale = 2)
    private BigDecimal totalAmount;

    private LocalDate expectedDeliveryDate;

    @Column(length = 30)
    private String paymentMethod;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(length = 50)
    private String createdBy;

    private Long createdByUserId;

    @Column(length = 500)
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "待确认";
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
