package com.company.material.repository;

import com.company.material.entity.ArrivalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArrivalItemRepository extends JpaRepository<ArrivalItem, Long> {
    List<ArrivalItem> findByArrivalId(Long arrivalId);

    List<ArrivalItem> findByArrivalIdIn(List<Long> arrivalIds);

    @Query("SELECT ai.materialId, SUM(ai.quantity) FROM ArrivalItem ai WHERE ai.arrivalId IN :arrivalIds GROUP BY ai.materialId")
    List<Object[]> sumQuantityByMaterialGrouped(@Param("arrivalIds") List<Long> arrivalIds);
}
