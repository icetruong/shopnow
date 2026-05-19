# TODO — Kafka Consumers chưa implement

## 1. Consumer: `payment.processed`

**Publisher:** Payment Service  
**Trigger:** Sau khi xử lý thanh toán

**Logic:**
```
status = SUCCESS → gọi inventoryService.deductOrder(orderId)
status = FAILED  → gọi inventoryService.releaseOrder(orderId, reason=PAYMENT_FAILED)
```

**Payload cần dùng:**
```json
{
  "orderId": "order-uuid-1",
  "status":  "SUCCESS | FAILED"
}
```

---

## 2. Consumer: `order.cancelled`

**Publisher:** Order Service  
**Trigger:** Khi user hoặc hệ thống hủy đơn hàng

**Logic:**
```
→ gọi inventoryService.releaseOrder(orderId, reason=ORDER_CANCELLED)
```

**Payload cần dùng:**
```json
{
  "orderId": "order-uuid-1"
}
```