package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "stock_in_orders")
public class StockInOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String stockInNo;

    private Long arrivalId;

    @Column(length = 20)
    private String arrivalNo;

    private Long orderId;

    @Column(length = 20)
    private String orderNo;

    private Long warehouseId;

    @Column(length = 50)
    private String warehouseName;

    @Column(length = 50)
    private String receivedBy;

    @Column(length = 10)
    private String status;

    @Column(length = 200)
    private String remark;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "已入库";
    }
}
