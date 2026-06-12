package com.company.material.repository;

import com.company.material.entity.StockInItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockInItemRepository extends JpaRepository<StockInItem, Long> {
    List<StockInItem> findByStockInId(Long stockInId);

    List<StockInItem> findByStockInIdIn(List<Long> stockInIds);

    void deleteByStockInId(Long stockInId);
}
