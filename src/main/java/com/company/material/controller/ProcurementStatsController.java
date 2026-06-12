package com.company.material.controller;

import com.company.material.entity.*;
import com.company.material.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/procurement-stats")
@RequiredArgsConstructor
public class ProcurementStatsController {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository orderItemRepository;
    private final PurchaseRequestRepository requestRepository;
    private final ArrivalRecordRepository arrivalRecordRepository;
    private final ArrivalItemRepository arrivalItemRepository;

    @GetMapping("/monthly-amount")
    public ResponseEntity<?> monthlyAmount(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start;
        LocalDateTime end;

        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            start = ym.atDay(1).atStartOfDay();
            end = ym.atEndOfMonth().atTime(23, 59, 59);
        } else if (year != null) {
            start = LocalDate.of(year, 1, 1).atStartOfDay();
            end = LocalDate.of(year, 12, 31).atTime(23, 59, 59);
        } else {
            YearMonth current = YearMonth.now();
            start = current.minusMonths(11).atDay(1).atStartOfDay();
            end = current.atEndOfMonth().atTime(23, 59, 59);
        }

        List<PurchaseOrder> orders = orderRepository.findByCreatedAtBetween(start, end);

        Map<String, BigDecimal> monthlyMap = new TreeMap<>();
        for (PurchaseOrder order : orders) {
            if ("已取消".equals(order.getStatus())) continue;
            String monthKey = order.getCreatedAt().getYear() + "-" +
                    String.format("%02d", order.getCreatedAt().getMonthValue());
            monthlyMap.merge(monthKey,
                    order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO,
                    BigDecimal::add);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : monthlyMap.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("month", entry.getKey());
            item.put("totalAmount", entry.getValue());
            item.put("orderCount", orders.stream()
                    .filter(o -> {
                        String key = o.getCreatedAt().getYear() + "-" +
                                String.format("%02d", o.getCreatedAt().getMonthValue());
                        return key.equals(entry.getKey()) && !"已取消".equals(o.getStatus());
                    }).count());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/supplier-ranking")
    public ResponseEntity<?> supplierRanking(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start;
        LocalDateTime end;

        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            start = ym.atDay(1).atStartOfDay();
            end = ym.atEndOfMonth().atTime(23, 59, 59);
        } else if (year != null) {
            start = LocalDate.of(year, 1, 1).atStartOfDay();
            end = LocalDate.of(year, 12, 31).atTime(23, 59, 59);
        } else {
            start = LocalDate.of(LocalDate.now().getYear(), 1, 1).atStartOfDay();
            end = LocalDateTime.now();
        }

        List<PurchaseOrder> orders = orderRepository.findByCreatedAtBetween(start, end);

        Map<Long, BigDecimal> supplierAmountMap = new HashMap<>();
        Map<Long, String> supplierNameMap = new HashMap<>();
        Map<Long, Integer> supplierCountMap = new HashMap<>();

        for (PurchaseOrder order : orders) {
            if ("已取消".equals(order.getStatus())) continue;
            supplierAmountMap.merge(order.getSupplierId(),
                    order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO,
                    BigDecimal::add);
            supplierNameMap.putIfAbsent(order.getSupplierId(), order.getSupplierName());
            supplierCountMap.merge(order.getSupplierId(), 1, Integer::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : supplierAmountMap.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("supplierId", entry.getKey());
            item.put("supplierName", supplierNameMap.get(entry.getKey()));
            item.put("totalAmount", entry.getValue());
            item.put("orderCount", supplierCountMap.getOrDefault(entry.getKey(), 0));
            result.add(item);
        }

        result.sort((a, b) -> ((BigDecimal) b.get("totalAmount")).compareTo((BigDecimal) a.get("totalAmount")));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/material-ranking")
    public ResponseEntity<?> materialRanking(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start;
        LocalDateTime end;

        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            start = ym.atDay(1).atStartOfDay();
            end = ym.atEndOfMonth().atTime(23, 59, 59);
        } else if (year != null) {
            start = LocalDate.of(year, 1, 1).atStartOfDay();
            end = LocalDate.of(year, 12, 31).atTime(23, 59, 59);
        } else {
            start = LocalDate.of(LocalDate.now().getYear(), 1, 1).atStartOfDay();
            end = LocalDateTime.now();
        }

        List<PurchaseOrder> orders = orderRepository.findByCreatedAtBetween(start, end);
        List<Long> orderIds = orders.stream()
                .filter(o -> !"已取消".equals(o.getStatus()))
                .map(PurchaseOrder::getId)
                .collect(Collectors.toList());

        if (orderIds.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<PurchaseOrderItem> items = orderItemRepository.findByOrderIdIn(orderIds);

        Map<Long, BigDecimal> materialQtyMap = new HashMap<>();
        Map<Long, BigDecimal> materialAmountMap = new HashMap<>();
        Map<Long, String> materialNameMap = new HashMap<>();
        Map<Long, String> materialCodeMap = new HashMap<>();

        for (PurchaseOrderItem item : items) {
            materialQtyMap.merge(item.getMaterialId(), item.getQuantity(), BigDecimal::add);
            materialAmountMap.merge(item.getMaterialId(),
                    item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO,
                    BigDecimal::add);
            materialNameMap.putIfAbsent(item.getMaterialId(), item.getMaterialName());
            materialCodeMap.putIfAbsent(item.getMaterialId(), item.getMaterialCode());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : materialQtyMap.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("materialId", entry.getKey());
            item.put("materialCode", materialCodeMap.get(entry.getKey()));
            item.put("materialName", materialNameMap.get(entry.getKey()));
            item.put("totalQuantity", entry.getValue());
            item.put("totalAmount", materialAmountMap.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            result.add(item);
        }

        result.sort((a, b) -> ((BigDecimal) b.get("totalQuantity")).compareTo((BigDecimal) a.get("totalQuantity")));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/approval-duration")
    public ResponseEntity<?> approvalDuration(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<PurchaseRequest> requests;
        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);
            requests = requestRepository.findByCreatedAtBetween(start, end);
        } else if (year != null) {
            LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
            LocalDateTime end = LocalDate.of(year, 12, 31).atTime(23, 59, 59);
            requests = requestRepository.findByCreatedAtBetween(start, end);
        } else {
            requests = requestRepository.findAll();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        double totalHours = 0;
        int count = 0;

        for (PurchaseRequest req : requests) {
            if (req.getApprovedAt() != null && req.getCreatedAt() != null) {
                long hours = java.time.Duration.between(req.getCreatedAt(), req.getApprovedAt()).toHours();
                Map<String, Object> item = new HashMap<>();
                item.put("requestNo", req.getRequestNo());
                item.put("department", req.getDepartment());
                item.put("applicant", req.getApplicant());
                item.put("status", req.getStatus());
                item.put("createdAt", req.getCreatedAt());
                item.put("approvedAt", req.getApprovedAt());
                item.put("approvalHours", hours);
                result.add(item);
                totalHours += hours;
                count++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("details", result);
        response.put("totalCount", count);
        response.put("averageHours", count > 0 ? Math.round(totalHours / count * 100.0) / 100.0 : 0);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/arrival-on-time-rate")
    public ResponseEntity<?> arrivalOnTimeRate(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start;
        LocalDateTime end;

        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            start = ym.atDay(1).atStartOfDay();
            end = ym.atEndOfMonth().atTime(23, 59, 59);
        } else if (year != null) {
            start = LocalDate.of(year, 1, 1).atStartOfDay();
            end = LocalDate.of(year, 12, 31).atTime(23, 59, 59);
        } else {
            start = LocalDate.of(LocalDate.now().getYear(), 1, 1).atStartOfDay();
            end = LocalDateTime.now();
        }

        List<PurchaseOrder> orders = orderRepository.findByCreatedAtBetween(start, end);
        orders = orders.stream()
                .filter(o -> !"已取消".equals(o.getStatus()))
                .filter(o -> o.getExpectedDeliveryDate() != null)
                .collect(Collectors.toList());

        int totalOrders = orders.size();
        int onTimeOrders = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (PurchaseOrder order : orders) {
            List<ArrivalRecord> arrivals = arrivalRecordRepository.findByOrderId(order.getId());
            LocalDate firstArrival = null;
            if (!arrivals.isEmpty()) {
                firstArrival = arrivals.stream()
                        .map(ArrivalRecord::getArrivalDate)
                        .filter(Objects::nonNull)
                        .map(ad -> ad.toLocalDate())
                        .min(LocalDate::compareTo)
                        .orElse(null);
            }

            boolean onTime = false;
            if (firstArrival != null) {
                onTime = !firstArrival.isAfter(order.getExpectedDeliveryDate());
                if (onTime) onTimeOrders++;
            }

            Map<String, Object> detail = new HashMap<>();
            detail.put("orderNo", order.getOrderNo());
            detail.put("supplierName", order.getSupplierName());
            detail.put("expectedDeliveryDate", order.getExpectedDeliveryDate());
            detail.put("firstArrivalDate", firstArrival);
            detail.put("onTime", onTime);
            detail.put("hasArrival", firstArrival != null);
            details.add(detail);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("totalOrders", totalOrders);
        response.put("onTimeOrders", onTimeOrders);
        response.put("onTimeRate", totalOrders > 0 ? Math.round((double) onTimeOrders / totalOrders * 10000.0) / 100.0 : 0);
        response.put("details", details);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/price-trend/{materialId}")
    public ResponseEntity<?> priceTrend(
            @PathVariable Long materialId,
            @RequestParam(required = false) Integer year) {
        List<PurchaseOrderItem> allItems = orderItemRepository.findByMaterialId(materialId);

        List<Long> orderIds = allItems.stream().map(PurchaseOrderItem::getOrderId).distinct().toList();
        Map<Long, PurchaseOrder> orderMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            orderRepository.findAllById(orderIds).forEach(o -> orderMap.put(o.getId(), o));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (PurchaseOrderItem item : allItems) {
            PurchaseOrder order = orderMap.get(item.getOrderId());
            if (order == null || "已取消".equals(order.getStatus())) continue;
            if (year != null && order.getCreatedAt().getYear() != year) continue;

            Map<String, Object> point = new HashMap<>();
            point.put("orderNo", order.getOrderNo());
            point.put("orderDate", order.getCreatedAt());
            point.put("supplierId", order.getSupplierId());
            point.put("supplierName", order.getSupplierName());
            point.put("unitPrice", item.getUnitPrice());
            point.put("quantity", item.getQuantity());
            result.add(point);
        }

        result.sort((a, b) -> ((LocalDateTime) a.get("orderDate")).compareTo((LocalDateTime) b.get("orderDate")));
        return ResponseEntity.ok(result);
    }
}
