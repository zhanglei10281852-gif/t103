package com.company.material.repository;

import com.company.material.entity.StockInOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockInOrderRepository extends JpaRepository<StockInOrder, Long> {
    Optional<StockInOrder> findByStockInNo(String stockInNo);

    List<StockInOrder> findByArrivalId(Long arrivalId);

    List<StockInOrder> findByOrderId(Long orderId);

    Page<StockInOrder> findByWarehouseId(Long warehouseId, Pageable pageable);

    @Query("SELECT MAX(s.stockInNo) FROM StockInOrder s WHERE s.stockInNo LIKE :prefix%")
    String findMaxStockInNoByPrefix(@Param("prefix") String prefix);
}
