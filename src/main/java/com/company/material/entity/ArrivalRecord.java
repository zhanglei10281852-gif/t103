package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "arrival_records")
public class ArrivalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(length = 20)
    private String orderNo;

    private LocalDateTime arrivalDate;

    @Column(length = 10)
    private String inspectionResult;

    @Column(length = 200)
    private String inspectionRemark;

    @Column(length = 50)
    private String receivedBy;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.arrivalDate == null) this.arrivalDate = LocalDateTime.now();
    }
}
