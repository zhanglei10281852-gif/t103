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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository orderItemRepository;
    private final PurchaseRequestRepository requestRepository;
    private final PurchaseRequestItemRepository requestItemRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierQuotationRepository quotationRepository;
    private final MaterialRepository materialRepository;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") String role,
            @RequestBody Map<String, Object> body) {
        if (!"采购员".equals(role) && !"管理员".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "仅采购员或管理员可创建采购订单"));
        }

        Long supplierId = Long.valueOf(body.get("supplierId").toString());
        Supplier supplier = supplierRepository.findById(supplierId).orElse(null);
        if (supplier == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "供应商不存在"));
        }

        List<Long> requestIds = new ArrayList<>();
        if (body.get("requestIds") != null) {
            for (Object rid : (List<?>) body.get("requestIds")) {
                requestIds.add(Long.valueOf(rid.toString()));
            }
        }

        for (Long reqId : requestIds) {
            PurchaseRequest req = requestRepository.findById(reqId).orElse(null);
            if (req == null || !"已批准".equals(req.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "采购申请" + reqId + "不存在或未批准"));
            }
        }

        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) body.get("items");
        if (itemsData == null || itemsData.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "订单明细不能为空"));
        }

        String orderNo = generateOrderNo();

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(orderNo);
        order.setSupplierId(supplierId);
        order.setSupplierName(supplier.getName());
        order.setPaymentMethod((String) body.get("paymentMethod"));
        order.setCreatedByUserId(userId);
        order.setStatus("待确认");
        order.setRemark((String) body.get("remark"));

        if (body.get("expectedDeliveryDate") != null) {
            order.setExpectedDeliveryDate(LocalDate.parse(body.get("expectedDeliveryDate").toString()));
        }

        User creator = new User();
        creator.setId(userId);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PurchaseOrderItem> items = new ArrayList<>();

        for (Map<String, Object> itemData : itemsData) {
            Long materialId = Long.valueOf(itemData.get("materialId").toString());
            BigDecimal quantity = new BigDecimal(itemData.get("quantity").toString());
            BigDecimal unitPrice = new BigDecimal(itemData.get("unitPrice").toString());
            BigDecimal amount = quantity.multiply(unitPrice);

            Material material = materialRepository.findById(materialId).orElse(null);
            if (material == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "物料ID" + materialId + "不存在"));
            }

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setOrderId(0L);
            item.setMaterialId(materialId);
            item.setMaterialCode(material.getMaterialCode());
            item.setMaterialName(material.getName());
            item.setQuantity(quantity);
            item.setUnit(material.getUnit());
            item.setUnitPrice(unitPrice);
            item.setAmount(amount);

            items.add(item);
            totalAmount = totalAmount.add(amount);
        }

        order.setTotalAmount(totalAmount);
        PurchaseOrder saved = orderRepository.save(order);

        for (PurchaseOrderItem item : items) {
            item.setOrderId(saved.getId());
            orderItemRepository.save(item);
        }

        for (Long reqId : requestIds) {
            PurchaseRequest req = requestRepository.findById(reqId).orElse(null);
            if (req != null) {
                req.setStatus("已转订单");
                requestRepository.save(req);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("order", saved);
        result.put("items", items);
        result.put("linkedRequestIds", requestIds);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String keyword) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PurchaseOrder> result;

        if (keyword != null && !keyword.isBlank()) {
            result = orderRepository.search(keyword, pr);
        } else if (status != null && !status.isBlank()) {
            result = orderRepository.findByStatus(status, pr);
        } else if (supplierId != null) {
            result = orderRepository.findBySupplierId(supplierId, pr);
        } else {
            result = orderRepository.findAll(pr);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return orderRepository.findById(id).map(order -> {
            List<PurchaseOrderItem> items = orderItemRepository.findByOrderId(order.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("order", order);
            result.put("items", items);
            return ResponseEntity.ok((Object) result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") String role,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (!"采购员".equals(role) && !"管理员".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "仅采购员或管理员可修改"));
        }

        return orderRepository.findById(id).map(order -> {
            if (!"待确认".equals(order.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "只能修改待确认的订单"));
            }

            if (body.get("paymentMethod") != null) order.setPaymentMethod(body.get("paymentMethod").toString());
            if (body.get("remark") != null) order.setRemark(body.get("remark").toString());
            if (body.get("expectedDeliveryDate") != null) {
                order.setExpectedDeliveryDate(LocalDate.parse(body.get("expectedDeliveryDate").toString()));
            }

            if (body.get("supplierId") != null) {
                Long supplierId = Long.valueOf(body.get("supplierId").toString());
                Supplier supplier = supplierRepository.findById(supplierId).orElse(null);
                if (supplier != null) {
                    order.setSupplierId(supplierId);
                    order.setSupplierName(supplier.getName());
                }
            }

            if (body.get("items") != null) {
                orderItemRepository.deleteByOrderId(order.getId());
                List<Map<String, Object>> itemsData = (List<Map<String, Object>>) body.get("items");
                BigDecimal totalAmount = BigDecimal.ZERO;
                for (Map<String, Object> itemData : itemsData) {
                    Long materialId = Long.valueOf(itemData.get("materialId").toString());
                    BigDecimal quantity = new BigDecimal(itemData.get("quantity").toString());
                    BigDecimal unitPrice = new BigDecimal(itemData.get("unitPrice").toString());
                    BigDecimal amount = quantity.multiply(unitPrice);
                    Material material = materialRepository.findById(materialId).orElse(null);

                    PurchaseOrderItem item = new PurchaseOrderItem();
                    item.setOrderId(order.getId());
                    item.setMaterialId(materialId);
                    item.setMaterialCode(material != null ? material.getMaterialCode() : "");
                    item.setMaterialName(material != null ? material.getName() : "");
                    item.setQuantity(quantity);
                    item.setUnit(material != null ? material.getUnit() : "");
                    item.setUnitPrice(unitPrice);
                    item.setAmount(amount);
                    orderItemRepository.save(item);
                    totalAmount = totalAmount.add(amount);
                }
                order.setTotalAmount(totalAmount);
            }

            orderRepository.save(order);
            List<PurchaseOrderItem> items = orderItemRepository.findByOrderId(order.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("order", order);
            result.put("items", items);
            return ResponseEntity.ok((Object) result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") String role,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        List<String> validStatuses = Arrays.asList("待确认", "已下单", "部分到货", "全部到货", "已完成", "已取消");
        if (newStatus == null || !validStatuses.contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的订单状态"));
        }

        return orderRepository.findById(id).map(order -> {
            order.setStatus(newStatus);
            orderRepository.save(order);
            return ResponseEntity.ok(Map.of("message", "状态更新成功", "order", order));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/from-requests")
    public ResponseEntity<?> getApprovedRequests() {
        List<PurchaseRequest> approved = requestRepository.findByStatusIn(Arrays.asList("已批准", "已转订单"));
        return ResponseEntity.ok(approved);
    }

    @GetMapping("/supplier-quotations/{materialId}")
    public ResponseEntity<?> getSupplierQuotations(@PathVariable Long materialId) {
        List<SupplierQuotation> quotations = quotationRepository.findByMaterialIdOrderByQuotationPriceAsc(materialId);
        return ResponseEntity.ok(quotations);
    }

    @PostMapping("/supplier-quotations")
    public ResponseEntity<?> addSupplierQuotation(@RequestBody Map<String, Object> body) {
        Long supplierId = Long.valueOf(body.get("supplierId").toString());
        Long materialId = Long.valueOf(body.get("materialId").toString());
        BigDecimal price = new BigDecimal(body.get("quotationPrice").toString());

        Supplier supplier = supplierRepository.findById(supplierId).orElse(null);
        Material material = materialRepository.findById(materialId).orElse(null);
        if (supplier == null || material == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "供应商或物料不存在"));
        }

        SupplierQuotation quotation = new SupplierQuotation();
        quotation.setSupplierId(supplierId);
        quotation.setSupplierName(supplier.getName());
        quotation.setMaterialId(materialId);
        quotation.setMaterialCode(material.getMaterialCode());
        quotation.setMaterialName(material.getName());
        quotation.setQuotationPrice(price);
        if (body.get("remark") != null) {
            quotation.setRemark(body.get("remark").toString());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(quotationRepository.save(quotation));
    }

    @GetMapping("/supplier-quotations")
    public ResponseEntity<?> listSupplierQuotations(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long supplierId) {
        if (materialId != null) {
            return ResponseEntity.ok(quotationRepository.findByMaterialIdOrderByQuotationPriceAsc(materialId));
        } else if (supplierId != null) {
            return ResponseEntity.ok(quotationRepository.findBySupplierId(supplierId));
        }
        return ResponseEntity.ok(quotationRepository.findAll());
    }

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "CG" + date;
        String maxNo = orderRepository.findMaxOrderNoByPrefix(prefix);
        int seq = 1;
        if (maxNo != null) {
            String seqStr = maxNo.substring(prefix.length());
            seq = Integer.parseInt(seqStr) + 1;
        }
        return prefix + String.format("%04d", seq);
    }
}
