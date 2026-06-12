package com.company.material.repository;

import com.company.material.entity.SupplierQuotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierQuotationRepository extends JpaRepository<SupplierQuotation, Long> {
    List<SupplierQuotation> findByMaterialId(Long materialId);

    List<SupplierQuotation> findByMaterialIdOrderByQuotationPriceAsc(Long materialId);

    List<SupplierQuotation> findBySupplierId(Long supplierId);

    List<SupplierQuotation> findBySupplierIdAndMaterialId(Long supplierId, Long materialId);
}
