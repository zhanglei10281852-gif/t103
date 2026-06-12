package com.company.material.repository;

import com.company.material.entity.PurchaseRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRequestItemRepository extends JpaRepository<PurchaseRequestItem, Long> {
    List<PurchaseRequestItem> findByRequestId(Long requestId);

    List<PurchaseRequestItem> findByRequestIdIn(List<Long> requestIds);

    void deleteByRequestId(Long requestId);
}
