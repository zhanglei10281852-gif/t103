package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "purchase_order_request_links")
public class PurchaseOrderRequestLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long requestId;
}
