package com.company.material.repository;

import com.company.material.entity.PurchaseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {
    Optional<PurchaseRequest> findByRequestNo(String requestNo);

    Page<PurchaseRequest> findByStatus(String status, Pageable pageable);

    Page<PurchaseRequest> findByDepartment(String department, Pageable pageable);

    Page<PurchaseRequest> findByApplicantUserId(Long applicantUserId, Pageable pageable);

    @Query("SELECT pr FROM PurchaseRequest pr WHERE pr.requestNo LIKE %:kw% OR pr.department LIKE %:kw% OR pr.applicant LIKE %:kw%")
    Page<PurchaseRequest> search(@Param("kw") String kw, Pageable pageable);

    @Query("SELECT MAX(pr.requestNo) FROM PurchaseRequest pr WHERE pr.requestNo LIKE :prefix%")
    String findMaxRequestNoByPrefix(@Param("prefix") String prefix);

    List<PurchaseRequest> findByStatusIn(List<String> statuses);

    long countByStatus(String status);

    @Query("SELECT pr FROM PurchaseRequest pr WHERE pr.createdAt BETWEEN :start AND :end")
    List<PurchaseRequest> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
