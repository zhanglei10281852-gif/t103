package com.company.material.repository;

import com.company.material.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    List<PurchaseOrderItem> findByOrderId(Long orderId);

    List<PurchaseOrderItem> findByOrderIdIn(List<Long> orderIds);

    List<PurchaseOrderItem> findByMaterialId(Long materialId);

    void deleteByOrderId(Long orderId);
}
