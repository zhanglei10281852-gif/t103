package com.company.material.controller;

import com.company.material.entity.*;
import com.company.material.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/arrivals")
@RequiredArgsConstructor
public class ArrivalController {

    private final ArrivalRecordRepository arrivalRecordRepository;
    private final ArrivalItemRepository arrivalItemRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository orderItemRepository;
    private final StockInOrderRepository stockInOrderRepository;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestAttribute("userId") Long userId,
            @RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        PurchaseOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "采购订单不存在"));
        }
        if ("已取消".equals(order.getStatus()) || "已完成".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "该订单已取消或已完成，无法登记到货"));
        }

        String inspectionResult = (String) body.get("inspectionResult");
        if (inspectionResult == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "质检结果为必填"));
        }

        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) body.get("items");
        if (itemsData == null || itemsData.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "到货物料明细不能为空"));
        }

        ArrivalRecord record = new ArrivalRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setInspectionResult(inspectionResult);
        record.setInspectionRemark((String) body.get("inspectionRemark"));
        record.setReceivedBy((String) body.get("receivedBy"));

        ArrivalRecord saved = arrivalRecordRepository.save(record);

        List<ArrivalItem> items = new ArrayList<>();
        for (Map<String, Object> itemData : itemsData) {
            Long materialId = Long.valueOf(itemData.get("materialId").toString());
            BigDecimal quantity = new BigDecimal(itemData.get("quantity").toString());

            ArrivalItem item = new ArrivalItem();
            item.setArrivalId(saved.getId());
            item.setMaterialId(materialId);
            item.setMaterialCode((String) itemData.get("materialCode"));
            item.setMaterialName((String) itemData.get("materialName"));
            item.setQuantity(quantity);
            item.setUnit((String) itemData.get("unit"));
            items.add(arrivalItemRepository.save(item));
        }

        updateOrderArrivalStatus(orderId);

        Map<String, Object> result = new HashMap<>();
        result.put("arrivalRecord", saved);
        result.put("items", items);
        if ("合格".equals(inspectionResult)) {
            result.put("nextAction", "质检合格，请调用 POST /api/stock-in/from-arrival/" + saved.getId() + " 生成入库单");
        } else {
            result.put("nextAction", "质检不合格，已登记到货台账，无法入库");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String inspectionResult) {
        if (orderId != null) {
            List<ArrivalRecord> records = arrivalRecordRepository.findByOrderId(orderId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (ArrivalRecord r : records) {
                Map<String, Object> map = new HashMap<>();
                map.put("record", r);
                map.put("items", arrivalItemRepository.findByArrivalId(r.getId()));
                result.add(map);
            }
            return ResponseEntity.ok(result);
        }
        if (inspectionResult != null) {
            return ResponseEntity.ok(arrivalRecordRepository.findByInspectionResult(inspectionResult));
        }
        return ResponseEntity.ok(arrivalRecordRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return arrivalRecordRepository.findById(id).map(record -> {
            List<ArrivalItem> items = arrivalItemRepository.findByArrivalId(record.getId());
            List<StockInOrder> stockIns = stockInOrderRepository.findByArrivalId(record.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("record", record);
            result.put("items", items);
            result.put("stockInOrders", stockIns);
            result.put("stockInCreated", !stockIns.isEmpty());
            return ResponseEntity.ok((Object) result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/order/{orderId}/summary")
    public ResponseEntity<?> getOrderArrivalSummary(@PathVariable Long orderId) {
        PurchaseOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        List<PurchaseOrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        List<ArrivalRecord> arrivals = arrivalRecordRepository.findByOrderId(orderId);

        List<Long> arrivalIds = arrivals.stream().map(ArrivalRecord::getId).toList();
        Map<Long, BigDecimal> arrivedQtyMap = new HashMap<>();

        if (!arrivalIds.isEmpty()) {
            List<Object[]> qtyResults = arrivalItemRepository.sumQuantityByMaterialGrouped(arrivalIds);
            for (Object[] row : qtyResults) {
                Long materialId = Long.valueOf(row[0].toString());
                BigDecimal totalQty = new BigDecimal(row[1].toString());
                arrivedQtyMap.put(materialId, totalQty);
            }
        }

        List<Map<String, Object>> summary = new ArrayList<>();
        for (PurchaseOrderItem oi : orderItems) {
            Map<String, Object> item = new HashMap<>();
            item.put("materialId", oi.getMaterialId());
            item.put("materialName", oi.getMaterialName());
            item.put("orderQuantity", oi.getQuantity());
            item.put("arrivedQuantity", arrivedQtyMap.getOrDefault(oi.getMaterialId(), BigDecimal.ZERO));
            BigDecimal ordered = oi.getQuantity();
            BigDecimal arrived = arrivedQtyMap.getOrDefault(oi.getMaterialId(), BigDecimal.ZERO);
            item.put("pendingQuantity", ordered.subtract(arrived).max(BigDecimal.ZERO));
            item.put("fullyArrived", arrived.compareTo(ordered) >= 0);
            summary.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", order.getOrderNo());
        result.put("orderStatus", order.getStatus());
        result.put("details", summary);
        return ResponseEntity.ok(result);
    }

    private void updateOrderArrivalStatus(Long orderId) {
        PurchaseOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return;

        if ("待确认".equals(order.getStatus())) {
            order.setStatus("已下单");
            orderRepository.save(order);
        }

        List<PurchaseOrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        List<ArrivalRecord> arrivals = arrivalRecordRepository.findByOrderId(orderId);

        List<Long> arrivalIds = arrivals.stream().map(ArrivalRecord::getId).toList();
        Map<Long, BigDecimal> arrivedQtyMap = new HashMap<>();

        if (!arrivalIds.isEmpty()) {
            List<Object[]> qtyResults = arrivalItemRepository.sumQuantityByMaterialGrouped(arrivalIds);
            for (Object[] row : qtyResults) {
                Long materialId = Long.valueOf(row[0].toString());
                BigDecimal totalQty = new BigDecimal(row[1].toString());
                arrivedQtyMap.put(materialId, totalQty);
            }
        }

        boolean allArrived = true;
        boolean anyArrived = false;
        for (PurchaseOrderItem oi : orderItems) {
            BigDecimal arrived = arrivedQtyMap.getOrDefault(oi.getMaterialId(), BigDecimal.ZERO);
            if (arrived.compareTo(BigDecimal.ZERO) > 0) anyArrived = true;
            if (arrived.compareTo(oi.getQuantity()) < 0) allArrived = false;
        }

        if (allArrived && !orderItems.isEmpty()) {
            order.setStatus("全部到货");
        } else if (anyArrived) {
            order.setStatus("部分到货");
        }
        orderRepository.save(order);
    }
}
