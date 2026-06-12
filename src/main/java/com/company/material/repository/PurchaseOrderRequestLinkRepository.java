package com.company.material.repository;

import com.company.material.entity.PurchaseOrderRequestLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderRequestLinkRepository extends JpaRepository<PurchaseOrderRequestLink, Long> {
    List<PurchaseOrderRequestLink> findByOrderId(Long orderId);

    List<PurchaseOrderRequestLink> findByRequestId(Long requestId);

    List<PurchaseOrderRequestLink> findByOrderIdIn(List<Long> orderIds);

    void deleteByOrderId(Long orderId);
}
