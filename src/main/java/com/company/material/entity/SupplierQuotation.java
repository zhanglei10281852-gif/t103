package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "supplier_quotations")
public class SupplierQuotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long supplierId;

    @Column(length = 100)
    private String supplierName;

    @Column(nullable = false)
    private Long materialId;

    @Column(length = 30)
    private String materialCode;

    @Column(length = 100)
    private String materialName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quotationPrice;

    private LocalDateTime quotationDate;

    @Column(length = 200)
    private String remark;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.quotationDate == null) this.quotationDate = LocalDateTime.now();
    }
}
