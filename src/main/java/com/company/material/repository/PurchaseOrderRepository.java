package com.company.material.repository;

import com.company.material.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    Optional<PurchaseOrder> findByOrderNo(String orderNo);

    Page<PurchaseOrder> findByStatus(String status, Pageable pageable);

    Page<PurchaseOrder> findBySupplierId(Long supplierId, Pageable pageable);

    Page<PurchaseOrder> findByCreatedByUserId(Long userId, Pageable pageable);

    @Query("SELECT po FROM PurchaseOrder po WHERE po.orderNo LIKE %:kw% OR po.supplierName LIKE %:kw%")
    Page<PurchaseOrder> search(@Param("kw") String kw, Pageable pageable);

    @Query("SELECT MAX(po.orderNo) FROM PurchaseOrder po WHERE po.orderNo LIKE :prefix%")
    String findMaxOrderNoByPrefix(@Param("prefix") String prefix);

    @Query("SELECT po FROM PurchaseOrder po WHERE po.createdAt BETWEEN :start AND :end")
    List<PurchaseOrder> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<PurchaseOrder> findByStatusIn(List<String> statuses);
}
