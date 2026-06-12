package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "purchase_request_items")
public class PurchaseRequestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private Long materialId;

    @Column(length = 30)
    private String materialCode;

    @Column(length = 100)
    private String materialName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(length = 20)
    private String unit;

    private LocalDate expectedDate;

    @Column(length = 200)
    private String purpose;

    @Column(precision = 12, scale = 2)
    private BigDecimal referencePrice;
}
