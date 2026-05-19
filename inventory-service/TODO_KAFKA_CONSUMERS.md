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

---

## 3. Scheduled Job: `expireReservations()`

**Chạy:** Mỗi 1 phút (`@Scheduled(fixedRate = 60000)`)

**Logic theo spec:**
```
1. SELECT stock_reservations WHERE status=RESERVED AND expires_at < NOW()
2. Với mỗi reservation hết hạn:
   a. SELECT FOR UPDATE inventory row
   b. UPDATE reservedQty -= qty
   c. UPDATE stock_reservations SET status = EXPIRED
3. Publish stock.released (reason = RESERVATION_EXPIRED)
```

---

## Ghi chú kiến trúc

- `POST /internal/stock/deduct` và `POST /internal/stock/release` vẫn giữ lại — dùng cho manual trigger khi cần can thiệp thủ công, không nằm trong happy flow tự động.
- Kafka Consumer group name nên đặt: `inventory-service-group` (theo convention project).
- Payload event chuẩn của project bọc trong `KafkaEvent<T>` wrapper (xem `KafkaEvent.java`).