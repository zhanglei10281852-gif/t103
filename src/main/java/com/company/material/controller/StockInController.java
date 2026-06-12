package com.company.material.controller;

import com.company.material.entity.*;
import com.company.material.repository.*;
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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock-in")
@RequiredArgsConstructor
public class StockInController {

    private final StockInOrderRepository stockInOrderRepository;
    private final StockInItemRepository stockInItemRepository;
    private final ArrivalRecordRepository arrivalRecordRepository;
    private final ArrivalItemRepository arrivalItemRepository;
    private final PurchaseOrderItemRepository orderItemRepository;
    private final PurchaseOrderRepository orderRepository;
    private final WarehouseRepository warehouseRepository;
    private final ArrivalController arrivalController;

    @PostMapping("/from-arrival/{arrivalId}")
    public ResponseEntity<?> createFromArrival(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("username") String username,
            @PathVariable Long arrivalId,
            @RequestBody Map<String, Object> body) {
        ArrivalRecord arrival = arrivalRecordRepository.findById(arrivalId).orElse(null);
        if (arrival == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"合格".equals(arrival.getInspectionResult())) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅质检合格的到货可生成入库单"));
        }

        List<StockInOrder> existing = stockInOrderRepository.findByArrivalId(arrivalId);
        if (!existing.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "该到货单已生成入库单，不可重复生成", "existingStockInId", existing.get(0).getId()));
        }

        Long warehouseId = body != null && body.get("warehouseId") != null
                ? Long.valueOf(body.get("warehouseId").toString()) : null;
        String warehouseName = null;
        if (warehouseId != null) {
            Warehouse w = warehouseRepository.findById(warehouseId).orElse(null);
            if (w == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "仓库不存在"));
            }
            warehouseName = w.getName();
        }

        String stockInNo = generateStockInNo();

        StockInOrder stockIn = new StockInOrder();
        stockIn.setStockInNo(stockInNo);
        stockIn.setArrivalId(arrivalId);
        stockIn.setOrderId(arrival.getOrderId());
        stockIn.setOrderNo(arrival.getOrderNo());
        stockIn.setWarehouseId(warehouseId);
        stockIn.setWarehouseName(warehouseName);
        stockIn.setReceivedBy(username);
        stockIn.setStatus("已入库");
        if (body != null && body.get("remark") != null) {
            stockIn.setRemark(body.get("remark").toString());
        }

        StockInOrder saved = stockInOrderRepository.save(stockIn);

        List<ArrivalItem> arrivalItems = arrivalItemRepository.findByArrivalId(arrivalId);
        Map<Long, BigDecimal> unitPriceMap = new HashMap<>();
        if (arrival.getOrderId() != null) {
            List<PurchaseOrderItem> orderItems = orderItemRepository.findByOrderId(arrival.getOrderId());
            for (PurchaseOrderItem oi : orderItems) {
                unitPriceMap.put(oi.getMaterialId(), oi.getUnitPrice());
            }
        }

        for (ArrivalItem ai : arrivalItems) {
            StockInItem si = new StockInItem();
            si.setStockInId(saved.getId());
            si.setMaterialId(ai.getMaterialId());
            si.setMaterialCode(ai.getMaterialCode());
            si.setMaterialName(ai.getMaterialName());
            si.setQuantity(ai.getQuantity());
            si.setUnit(ai.getUnit());
            si.setUnitPrice(unitPriceMap.get(ai.getMaterialId()));
            stockInItemRepository.save(si);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("stockInOrder", saved);
        result.put("items", stockInItemRepository.findByStockInId(saved.getId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long warehouseId) {
        if (orderId != null) {
            List<StockInOrder> orders = stockInOrderRepository.findByOrderId(orderId);
            return ResponseEntity.ok(withItems(orders));
        }
        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StockInOrder> result;
        if (warehouseId != null) {
            result = stockInOrderRepository.findByWarehouseId(warehouseId, pr);
        } else {
            result = stockInOrderRepository.findAll(pr);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return stockInOrderRepository.findById(id).map(si -> {
            Map<String, Object> result = new HashMap<>();
            result.put("stockInOrder", si);
            result.put("items", stockInItemRepository.findByStockInId(si.getId()));
            return ResponseEntity.ok((Object) result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/arrival/{arrivalId}")
    public ResponseEntity<?> getByArrivalId(@PathVariable Long arrivalId) {
        List<StockInOrder> orders = stockInOrderRepository.findByArrivalId(arrivalId);
        return ResponseEntity.ok(withItems(orders));
    }

    private List<Map<String, Object>> withItems(List<StockInOrder> orders) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (StockInOrder o : orders) {
            Map<String, Object> m = new HashMap<>();
            m.put("stockInOrder", o);
            m.put("items", stockInItemRepository.findByStockInId(o.getId()));
            result.add(m);
        }
        return result;
    }

    private String generateStockInNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "RK" + date;
        String maxNo = stockInOrderRepository.findMaxStockInNoByPrefix(prefix);
        int seq = 1;
        if (maxNo != null) {
            String seqStr = maxNo.substring(prefix.length());
            seq = Integer.parseInt(seqStr) + 1;
        }
        return prefix + String.format("%04d", seq);
    }
}
