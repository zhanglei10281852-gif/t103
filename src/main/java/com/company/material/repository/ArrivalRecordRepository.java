package com.company.material.repository;

import com.company.material.entity.ArrivalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArrivalRecordRepository extends JpaRepository<ArrivalRecord, Long> {
    List<ArrivalRecord> findByOrderId(Long orderId);

    Page<ArrivalRecord> findByOrderId(Long orderId, Pageable pageable);

    List<ArrivalRecord> findByInspectionResult(String inspectionResult);
}
