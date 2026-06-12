package com.company.material.controller;

import com.company.material.entity.Material;
import com.company.material.entity.PurchaseRequest;
import com.company.material.entity.PurchaseRequestItem;
import com.company.material.entity.User;
import com.company.material.repository.MaterialRepository;
import com.company.material.repository.PurchaseRequestItemRepository;
import com.company.material.repository.PurchaseRequestRepository;
import com.company.material.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final PurchaseRequestRepository requestRepository;
    private final PurchaseRequestItemRepository requestItemRepository;
    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;

    private static final BigDecimal APPROVAL_THRESHOLD = new BigDecimal("50000");

    @PostMapping
    public ResponseEntity<?> create(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") String role,
            @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户不存在"));
        }

        String urgency = (String) body.get("urgency");
        String reason = (String) body.get("reason");
        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) body.get("items");

        if (urgency == null || itemsData == null || itemsData.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "紧急程度和物料明细为必填"));
        }

        String requestNo = generateRequestNo();

        PurchaseRequest request = new PurchaseRequest();
        request.setRequestNo(requestNo);
        request.setDepartment(user.getDepartment());
        request.setApplicant(user.getRealName());
        request.setApplicantUserId(userId);
        request.setUrgency(urgency);
        request.setReason(reason);
        request.setStatus("待审批");

        BigDecimal estimatedAmount = BigDecimal.ZERO;
        List<PurchaseRequestItem> items = new ArrayList<>();

        for (Map<String, Object> itemData : itemsData) {
            Long materialId = Long.valueOf(itemData.get("materialId").toString());
            Material material = materialRepository.findById(materialId).orElse(null);
            if (material == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "物料ID" + materialId + "不存在"));
            }

            BigDecimal quantity = new BigDecimal(itemData.get("quantity").toString());
            BigDecimal refPrice = material.getReferencePrice() != null ? material.getReferencePrice() : BigDecimal.ZERO;
            BigDecimal itemAmount = refPrice.multiply(quantity);

            PurchaseRequestItem item = new PurchaseRequestItem();
            item.setRequestId(0L);
            item.setMaterialId(materialId);
            item.setMaterialCode(material.getMaterialCode());
            item.setMaterialName(material.getName());
            item.setQuantity(quantity);
            item.setUnit(material.getUnit());
            item.setReferencePrice(refPrice);

            if (itemData.get("expectedDate") != null) {
                item.setExpectedDate(java.time.LocalDate.parse(itemData.get("expectedDate").toString()));
            }
            if (itemData.get("purpose") != null) {
                item.setPurpose(itemData.get("purpose").toString());
            }

            items.add(item);
            estimatedAmount = estimatedAmount.add(itemAmount);
        }

        request.setEstimatedAmount(estimatedAmount);
        PurchaseRequest saved = requestRepository.save(request);

        for (PurchaseRequestItem item : items) {
            item.setRequestId(saved.getId());
            requestItemRepository.save(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("request", saved);
        result.put("items", items);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PurchaseRequest> result;

        if (keyword != null && !keyword.isBlank()) {
            result = requestRepository.search(keyword, pr);
        } else if (status != null && !status.isBlank()) {
            result = requestRepository.findByStatus(status, pr);
        } else if (department != null && !department.isBlank()) {
            result = requestRepository.findByDepartment(department, pr);
        } else {
            result = requestRepository.findAll(pr);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return requestRepository.findById(id).map(req -> {
            List<PurchaseRequestItem> items = requestItemRepository.findByRequestId(req.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("request", req);
            result.put("items", items);
            return ResponseEntity.ok((Object) result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return requestRepository.findById(id).map(req -> {
            if (!req.getApplicantUserId().equals(userId) && !"管理员".equals(body.get("_role"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "只能修改自己的申请"));
            }
            if (!"待审批".equals(req.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "只能修改待审批的申请"));
            }

            if (body.get("urgency") != null) req.setUrgency(body.get("urgency").toString());
            if (body.get("reason") != null) req.setReason(body.get("reason").toString());

            requestRepository.save(req);

            if (body.get("items") != null) {
                requestItemRepository.deleteByRequestId(req.getId());
                List<Map<String, Object>> itemsData = (List<Map<String, Object>>) body.get("items");
                BigDecimal estimatedAmount = BigDecimal.ZERO;
                for (Map<String, Object> itemData : itemsData) {
                    Long materialId = Long.valueOf(itemData.get("materialId").toString());
                    Material material = materialRepository.findById(materialId).orElse(null);
                    if (material == null) continue;

                    BigDecimal quantity = new BigDecimal(itemData.get("quantity").toString());
                    BigDecimal refPrice = material.getReferencePrice() != null ? material.getReferencePrice() : BigDecimal.ZERO;

                    PurchaseRequestItem item = new PurchaseRequestItem();
                    item.setRequestId(req.getId());
                    item.setMaterialId(materialId);
                    item.setMaterialCode(material.getMaterialCode());
                    item.setMaterialName(material.getName());
                    item.setQuantity(quantity);
                    item.setUnit(material.getUnit());
                    item.setReferencePrice(refPrice);
                    if (itemData.get("expectedDate") != null) {
                        item.setExpectedDate(java.time.LocalDate.parse(itemData.get("expectedDate").toString()));
                    }
                    if (itemData.get("purpose") != null) {
                        item.setPurpose(itemData.get("purpose").toString());
                    }
                    requestItemRepository.save(item);
                    estimatedAmount = estimatedAmount.add(refPrice.multiply(quantity));
                }
                req.setEstimatedAmount(estimatedAmount);
                requestRepository.save(req);
            }

            List<PurchaseRequestItem> items = requestItemRepository.findByRequestId(req.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("request", req);
            result.put("items", items);
            return ResponseEntity.ok((Object) result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") String role,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return requestRepository.findById(id).map(req -> {
            if (!"待审批".equals(req.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "只能审批待审批的申请"));
            }

            boolean canApprove = false;
            if (req.getEstimatedAmount() != null && req.getEstimatedAmount().compareTo(APPROVAL_THRESHOLD) >= 0) {
                if ("总经理".equals(role) || "管理员".equals(role)) {
                    canApprove = true;
                } else {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "估算金额>=5万，需要总经理审批"));
                }
            } else {
                if ("部门主管".equals(role) || "总经理".equals(role) || "管理员".equals(role)) {
                    canApprove = true;
                } else {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "需要部门主管或总经理审批"));
                }
            }

            if (!canApprove) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "无审批权限"));
            }

            User approver = userRepository.findById(userId).orElse(null);
            req.setStatus("已批准");
            req.setApprovedBy(approver != null ? approver.getRealName() : "未知");
            req.setApprovedAt(LocalDateTime.now());
            requestRepository.save(req);
            return ResponseEntity.ok(Map.of("message", "审批通过", "request", req));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") String role,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return requestRepository.findById(id).map(req -> {
            if (!"待审批".equals(req.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "只能驳回待审批的申请"));
            }

            boolean canReject = "部门主管".equals(role) || "总经理".equals(role) || "管理员".equals(role);
            if (!canReject) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "无审批权限"));
            }

            String rejectReason = (String) body.get("reason");
            if (rejectReason == null || rejectReason.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "驳回原因不能为空"));
            }

            User approver = userRepository.findById(userId).orElse(null);
            req.setStatus("已驳回");
            req.setApprovedBy(approver != null ? approver.getRealName() : "未知");
            req.setApprovedAt(LocalDateTime.now());
            req.setRejectReason(rejectReason);
            requestRepository.save(req);
            return ResponseEntity.ok(Map.of("message", "已驳回", "request", req));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") String role,
            @PathVariable Long id) {
        return requestRepository.findById(id).map(req -> {
            if (!req.getApplicantUserId().equals(userId) && !"管理员".equals(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "只能删除自己的申请"));
            }
            if (!"待审批".equals(req.getStatus()) && !"已驳回".equals(req.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "只能删除待审批或已驳回的申请"));
            }
            requestItemRepository.deleteByRequestId(req.getId());
            requestRepository.deleteById(req.getId());
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }).orElse(ResponseEntity.notFound().build());
    }

    private String generateRequestNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "SQ" + date;
        String maxNo = requestRepository.findMaxRequestNoByPrefix(prefix);
        int seq = 1;
        if (maxNo != null) {
            String seqStr = maxNo.substring(prefix.length());
            seq = Integer.parseInt(seqStr) + 1;
        }
        return prefix + String.format("%04d", seq);
    }
}
