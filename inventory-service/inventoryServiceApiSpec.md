# Inventory Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8083/api/v1
```

---

# PHẦN 1 — API ENDPOINTS

---

## 1. STOCK — Quản lý tồn kho

---

### GET /internal/stock/{variantId}
Lấy tồn kho hiện tại của 1 variant. Cart Service gọi để kiểm tra trước khi thêm vào giỏ.

**Header:** `X-Internal-Token: {sharedSecret}`

**Response 200**
```json
{
  "variantId":   "var-uuid-1",
  "sku":         "POLO-WHITE-S",
  "stockQty":    50,
  "reservedQty": 5,
  "availableQty": 45,
  "status":      "IN_STOCK"
}
```

**Status values:**
- `IN_STOCK` — availableQty > 10
- `LOW_STOCK` — availableQty 1–10
- `OUT_OF_STOCK` — availableQty = 0

---

### GET /internal/stock/batch
Lấy tồn kho của nhiều variant cùng lúc. Cart Service gọi khi render giỏ hàng.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "variantIds": [
    "var-uuid-1",
    "var-uuid-2",
    "var-uuid-3"
  ]
}
```

**Response 200**
```json
{
  "data": [
    {
      "variantId":    "var-uuid-1",
      "availableQty": 45,
      "status":       "IN_STOCK"
    },
    {
      "variantId":    "var-uuid-2",
      "availableQty": 3,
      "status":       "LOW_STOCK"
    },
    {
      "variantId":    "var-uuid-3",
      "availableQty": 0,
      "status":       "OUT_OF_STOCK"
    }
  ]
}
```

---

### POST /internal/stock/reserve
Order Service gọi trong Saga flow để giữ hàng khi tạo đơn.
Dùng **Pessimistic Lock** — lock row cho đến khi transaction xong.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "orderId": "order-uuid-1",
  "items": [
    { "variantId": "var-uuid-1", "qty": 2 },
    { "variantId": "var-uuid-2", "qty": 1 }
  ]
}
```

**Response 200** — reserve thành công
```json
{
  "success":   true,
  "orderId":   "order-uuid-1",
  "reservedAt": "2024-01-15T10:30:00Z",
  "expiresAt":  "2024-01-15T10:45:00Z"
}
```

**Response 409** — không đủ hàng
```json
{
  "success": false,
  "errorCode":    "INSUFFICIENT_STOCK",
  "message": "Không đủ hàng.",
  "details": [
    {
      "variantId":    "var-uuid-2",
      "sku":          "POLO-WHITE-M",
      "requested":    3,
      "available":    1
    }
  ]
}
```

**Logic bên trong:**
```
1. Validate tất cả variantId tồn tại
2. BEGIN TRANSACTION
3. SELECT ... FOR UPDATE (pessimistic lock toàn bộ row cùng lúc, order by variantId để tránh deadlock)
4. Kiểm tra từng variant: availableQty = stockQty - reservedQty >= qty yêu cầu
5. Nếu bất kỳ variant nào không đủ → ROLLBACK → trả 409 kèm chi tiết
6. Nếu đủ tất cả → UPDATE reservedQty += qty cho từng variant
7. INSERT vào stock_reservations (orderId, variantId, qty, expiresAt = now + 15 phút)
8. COMMIT
9. Publish Kafka event stock.reserved
```

---

### POST /internal/stock/release
Giải phóng hàng đã reserve — gọi khi thanh toán thất bại hoặc user hủy đơn (Saga compensating).

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "orderId": "order-uuid-1",
  "reason":  "PAYMENT_FAILED"
}
```

**Reason values:** `PAYMENT_FAILED` / `ORDER_CANCELLED` / `RESERVATION_EXPIRED`

**Response 200**
```json
{
  "success":    true,
  "orderId":    "order-uuid-1",
  "releasedAt": "2024-01-15T10:35:00Z"
}
```

**Logic bên trong:**
```
1. Tìm tất cả stock_reservations theo orderId (status = RESERVED)
2. BEGIN TRANSACTION
3. SELECT ... FOR UPDATE các inventory row liên quan
4. UPDATE reservedQty -= qty cho từng variant
5. UPDATE stock_reservations SET status = RELEASED
6. COMMIT
7. Publish Kafka event stock.released
```

---

### POST /internal/stock/deduct
Trừ hàng thật sự khi thanh toán thành công — chuyển từ reserved sang đã bán.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "orderId": "order-uuid-1"
}
```

**Response 200**
```json
{
  "success":    true,
  "orderId":    "order-uuid-1",
  "deductedAt": "2024-01-15T10:36:00Z"
}
```

**Logic bên trong:**
```
1. Tìm stock_reservations theo orderId (status = RESERVED)
2. BEGIN TRANSACTION
3. SELECT ... FOR UPDATE
4. UPDATE stockQty -= qty (trừ thật)
5. UPDATE reservedQty -= qty (bỏ reserve)
6. UPDATE soldQty += qty
7. UPDATE stock_reservations SET status = DEDUCTED
8. COMMIT
9. Nếu stockQty còn lại <= threshold → publish Kafka event stock.low_warning
```

---

### POST /internal/stock/flash-sale/reserve
Reserve hàng flash sale — dùng **Redis DECR atomic** thay vì DB lock để chịu tải cao.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "flashSaleId": "fs-uuid-1",
  "variantId":   "var-uuid-1",
  "orderId":     "order-uuid-1",
  "qty":         1
}
```

**Response 200** — còn hàng
```json
{
  "success":    true,
  "remaining":  23,
  "reservedAt": "2024-01-15T20:00:05Z"
}
```

**Response 409** — hết hàng
```json
{
  "success": false,
  "code":    "FLASH_SALE_SOLD_OUT",
  "message": "Sản phẩm flash sale đã hết."
}
```

**Logic bên trong (Redis atomic — không dùng DB lock):**
```
1. Kiểm tra flash sale còn active không (Redis key flash:active:{flashSaleId})
2. Kiểm tra user chưa mua trong flash sale này (Redis key flash:user:{flashSaleId}:{userId})
3. DECR flash:stock:{flashSaleId}:{variantId}
4. Nếu kết quả < 0 → INCR lại (rollback) → trả 409
5. Nếu >= 0 → SET flash:user:{flashSaleId}:{userId} = "1" (TTL = thời gian flash sale)
6. Đẩy vào DB async (Kafka event để ghi lại lịch sử)
```

---

## 2. ADMIN STOCK — Quản lý tồn kho thủ công

---

### GET /admin/stock
Lấy danh sách tồn kho toàn bộ variant, có filter và phân trang.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Query Params**
```
page      = 0
size      = 20
variantId = var-uuid-1     (filter theo variant)
productId = prod-uuid-1    (filter theo product)
status    = LOW_STOCK       (IN_STOCK | LOW_STOCK | OUT_OF_STOCK)
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "variantId":    "var-uuid-1",
        "sku":          "POLO-WHITE-S",
        "productName":  "Áo Polo Nam Basic",
        "color":        "Trắng",
        "size":         "S",
        "stockQty":     50,
        "reservedQty":  5,
        "availableQty": 45,
        "soldQty":      340,
        "status":       "IN_STOCK",
        "updatedAt":    "2024-01-15T10:00:00Z"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 48,
    "totalPages":    3
  }
}
```

---

### POST /admin/stock/{variantId}/import
Nhập thêm hàng (nhập kho).

**Request Body**
```json
{
  "qty":   100,
  "note":  "Nhập hàng lô tháng 1/2024"
}
```

**Response 200**
```json
{
  "success":      true,
  "variantId":    "var-uuid-1",
  "previousQty":  50,
  "importedQty":  100,
  "currentQty":   150,
  "updatedAt":    "2024-01-15T14:00:00Z"
}
```

**Logic:** INSERT vào `stock_transactions` (type = IMPORT), UPDATE `stockQty += qty`.

---

### POST /admin/stock/{variantId}/adjust
Điều chỉnh tồn kho thủ công (kiểm kê, sai lệch...).

**Request Body**
```json
{
  "newQty": 45,
  "note":   "Kiểm kê thực tế 15/01/2024"
}
```

**Response 200**
```json
{
  "success":      true,
  "variantId":    "var-uuid-1",
  "previousQty":  50,
  "adjustedQty":  45,
  "difference":   -5,
  "updatedAt":    "2024-01-15T14:00:00Z"
}
```

**Logic:** INSERT vào `stock_transactions` (type = ADJUSTMENT), UPDATE `stockQty = newQty`.

---

### GET /admin/stock/{variantId}/history
Lịch sử biến động tồn kho của 1 variant.

**Query Params**
```
page      = 0
size      = 20
startDate = 2024-01-01
endDate   = 2024-01-31
type      = IMPORT    (IMPORT | DEDUCT | RELEASE | ADJUSTMENT | FLASH_SALE)
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "transactionId": "txn-uuid-1",
        "type":          "DEDUCT",
        "qty":           -2,
        "qtyBefore":     52,
        "qtyAfter":      50,
        "orderId":       "order-uuid-1",
        "note":          "Trừ hàng đơn order-uuid-1",
        "createdAt":     "2024-01-15T10:36:00Z"
      },
      {
        "transactionId": "txn-uuid-2",
        "type":          "IMPORT",
        "qty":           100,
        "qtyBefore":     0,
        "qtyAfter":      100,
        "orderId":       null,
        "note":          "Nhập hàng lô tháng 1/2024",
        "createdAt":     "2024-01-10T08:00:00Z"
      }
    ],
    "page":          0,
    "totalElements": 25
  }
}
```

---

## 3. FLASH SALE STOCK — Khởi tạo flash sale

---

### POST /admin/flash-sale-stock
Admin khởi tạo tồn kho flash sale — ghi vào Redis trước khi flash sale bắt đầu.

**Request Body**
```json
{
  "flashSaleId": "fs-uuid-1",
  "startsAt":    "2024-01-15T20:00:00Z",
  "endsAt":      "2024-01-15T22:00:00Z",
  "items": [
    { "variantId": "var-uuid-1", "flashSaleQty": 50 },
    { "variantId": "var-uuid-2", "flashSaleQty": 30 }
  ]
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã khởi tạo tồn kho flash sale thành công."
}
```

**Logic:**
```
1. Validate flashSaleQty <= stockQty thực tế của từng variant
2. SET flash:stock:{flashSaleId}:{variantId} = flashSaleQty (TTL = thời điểm endsAt)
3. SET flash:active:{flashSaleId} = "1" (TTL = thời điểm endsAt)
4. INSERT vào flash_sale_stocks (lưu lại để audit)
```

---

## 4. KAFKA EVENTS — Consume

---

### Consumer: payment.processed
Khi thanh toán thành công → gọi nội bộ deduct stock.
Khi thanh toán thất bại → gọi nội bộ release stock.

---

### Consumer: order.cancelled
Khi đơn bị hủy → release stock.

---

## 5. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `INSUFFICIENT_STOCK` | 409 | Không đủ hàng để reserve |
| `FLASH_SALE_SOLD_OUT` | 409 | Hết hàng flash sale |
| `FLASH_SALE_USER_LIMIT` | 409 | User đã mua trong flash sale này |
| `INVENTORY_NOT_FOUND` | 404 | Không tìm thấy inventory theo variantId |
| `RESERVATION_NOT_FOUND` | 404 | Không tìm thấy reservation theo orderId |
| `STOCK_NOT_FOUND` | 404 | Không tìm thấy variant trong kho |
| `INVALID_QTY` | 400 | qty phải > 0 |

---

## 6. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| GET | /internal/stock/{variantId} | 🔒 Internal | — |
| GET | /internal/stock/batch | 🔒 Internal | — |
| POST | /internal/stock/reserve | 🔒 Internal | — |
| POST | /internal/stock/release | 🔒 Internal | — |
| POST | /internal/stock/deduct | 🔒 Internal | — |
| POST | /internal/stock/flash-sale/reserve | 🔒 Internal | — |
| GET | /admin/stock | ✅ | ADMIN |
| POST | /admin/stock/{variantId}/import | ✅ | ADMIN |
| POST | /admin/stock/{variantId}/adjust | ✅ | ADMIN |
| GET | /admin/stock/{variantId}/history | ✅ | ADMIN |
| POST | /admin/flash-sale-stock | ✅ | ADMIN |

---

---

# PHẦN 2 — DATABASE SCHEMA

---

## Bảng: inventories

Bảng trung tâm — mỗi row là tồn kho của 1 variant.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| variant_id | UUID | NOT NULL, UNIQUE | FK logic sang Product Service (không FK vật lý vì khác DB) |
| sku | VARCHAR(100) | NOT NULL, UNIQUE | Copy từ Product Service để tiện query |
| stock_qty | INT | NOT NULL, DEFAULT 0 | Tổng hàng trong kho |
| reserved_qty | INT | NOT NULL, DEFAULT 0 | Đang được giữ chờ thanh toán |
| sold_qty | INT | NOT NULL, DEFAULT 0 | Đã bán thành công (chỉ tăng, không giảm) |
| low_stock_threshold | INT | NOT NULL, DEFAULT 10 | Ngưỡng cảnh báo sắp hết hàng |
| version | BIGINT | NOT NULL, DEFAULT 0 | Optimistic lock version (backup khi không dùng pessimistic) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Computed (không lưu trong DB, tính khi query):**
```sql
available_qty = stock_qty - reserved_qty
```

**Index:**
```sql
CREATE UNIQUE INDEX idx_inventories_variant_id ON inventories(variant_id);
CREATE UNIQUE INDEX idx_inventories_sku ON inventories(sku);
CREATE INDEX idx_inventories_stock_qty ON inventories(stock_qty);
```

**Tại sao không có FK vật lý sang Product Service?**
Vì 2 service dùng 2 database khác nhau (DB per service pattern).
`variant_id` ở đây là UUID reference logic — Inventory Service tự validate bằng cách gọi REST sang Product Service khi cần.

---

## Bảng: stock_reservations

Theo dõi hàng đang được giữ chờ thanh toán theo từng đơn hàng.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| order_id | UUID | NOT NULL | Reference sang Order Service |
| variant_id | UUID | NOT NULL | |
| qty | INT | NOT NULL | Số lượng đang giữ |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'RESERVED' | RESERVED / DEDUCTED / RELEASED / EXPIRED |
| expires_at | TIMESTAMP | NOT NULL | Tự động release nếu quá thời gian này |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_reservations_order_id ON stock_reservations(order_id);
CREATE INDEX idx_reservations_variant_id ON stock_reservations(variant_id);
CREATE INDEX idx_reservations_status ON stock_reservations(status);
CREATE INDEX idx_reservations_expires_at ON stock_reservations(expires_at)
    WHERE status = 'RESERVED';   -- Partial index, chỉ index row chưa xử lý
```

---

## Bảng: stock_transactions

Lịch sử toàn bộ biến động tồn kho — không bao giờ xóa, chỉ INSERT.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| variant_id | UUID | NOT NULL | |
| type | VARCHAR(20) | NOT NULL | IMPORT / DEDUCT / RELEASE / ADJUSTMENT / FLASH_SALE |
| qty | INT | NOT NULL | Dương = nhập, âm = xuất |
| qty_before | INT | NOT NULL | Tồn kho trước khi thay đổi |
| qty_after | INT | NOT NULL | Tồn kho sau khi thay đổi |
| order_id | UUID | NULLABLE | Có nếu liên quan đến đơn hàng |
| note | TEXT | NULLABLE | |
| created_by | UUID | NULLABLE | userId nếu là thao tác thủ công của admin |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_stock_txn_variant_id ON stock_transactions(variant_id);
CREATE INDEX idx_stock_txn_order_id ON stock_transactions(order_id);
CREATE INDEX idx_stock_txn_type ON stock_transactions(type);
CREATE INDEX idx_stock_txn_created_at ON stock_transactions(created_at DESC);
```

---

## Bảng: flash_sale_stocks

Lưu cấu hình tồn kho flash sale (để audit + restore nếu Redis mất).

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| flash_sale_id | UUID | NOT NULL | Reference sang Promotion Service |
| variant_id | UUID | NOT NULL | |
| initial_qty | INT | NOT NULL | Số lượng flash sale ban đầu |
| sold_qty | INT | NOT NULL, DEFAULT 0 | Đã bán trong flash sale |
| starts_at | TIMESTAMP | NOT NULL | |
| ends_at | TIMESTAMP | NOT NULL | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_flash_sale_stocks_flash_sale_id ON flash_sale_stocks(flash_sale_id);
CREATE UNIQUE INDEX idx_flash_sale_stocks_unique ON flash_sale_stocks(flash_sale_id, variant_id);
```

---

## Redis Keys — Inventory Service

| Key pattern | Value | TTL | Mục đích |
|-------------|-------|-----|---------|
| `flash:stock:{flashSaleId}:{variantId}` | INT (số lượng còn) | Đến lúc flash sale kết thúc | Counter atomic cho flash sale |
| `flash:active:{flashSaleId}` | `"1"` | Đến lúc flash sale kết thúc | Kiểm tra flash sale còn chạy không |
| `flash:user:{flashSaleId}:{userId}` | `"1"` | Đến lúc flash sale kết thúc | Giới hạn mỗi user mua 1 lần |

---

## Kafka Events

### Publish: stock.reserved
```json
{
  "eventId":   "uuid-v4",
  "eventType": "stock.reserved",
  "timestamp": "2024-01-15T10:30:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":    "order-uuid-1",
    "reservedAt": "2024-01-15T10:30:00Z",
    "expiresAt":  "2024-01-15T10:45:00Z",
    "items": [
      { "variantId": "var-uuid-1", "qty": 2 },
      { "variantId": "var-uuid-2", "qty": 1 }
    ]
  }
}
```
**Consumer:** Order Service (tiến hành bước tiếp theo trong Saga — gọi Payment)

---

### Publish: stock.released
```json
{
  "eventId":   "uuid-v4",
  "eventType": "stock.released",
  "timestamp": "2024-01-15T10:35:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":    "order-uuid-1",
    "reason":     "PAYMENT_FAILED",
    "releasedAt": "2024-01-15T10:35:00Z",
    "items": [
      { "variantId": "var-uuid-1", "qty": 2 },
      { "variantId": "var-uuid-2", "qty": 1 }
    ]
  }
}
```
**Consumer:** Order Service (cập nhật order status = CANCELLED)

---

### Publish: stock.low_warning
```json
{
  "eventId":   "uuid-v4",
  "eventType": "stock.low_warning",
  "timestamp": "2024-01-15T10:36:00Z",
  "version":   "1.0",
  "payload": {
    "variantId":    "var-uuid-1",
    "sku":          "POLO-WHITE-S",
    "currentStock": 3,
    "threshold":    10
  }
}
```
**Consumer:** Notification Service (gửi email cảnh báo cho admin)

---

---

# PHẦN 3 — PESSIMISTIC LOCK CHI TIẾT

Đây là phần quan trọng nhất của Inventory Service. Cần hiểu rõ để tránh bug.

---

## Vấn đề nếu không có lock

```
Thread A: SELECT stock_qty = 5, reserved_qty = 0  → availableQty = 5 ✅
Thread B: SELECT stock_qty = 5, reserved_qty = 0  → availableQty = 5 ✅
Thread A: UPDATE reserved_qty = 3  (order 3 cái)
Thread B: UPDATE reserved_qty = 4  (order 4 cái)
→ Tổng reserve = 7, nhưng chỉ có 5 cái → OVERSELL 💀
```

---

## Giải pháp: Pessimistic Lock với `SELECT FOR UPDATE`

```java
// Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.variantId IN :variantIds ORDER BY i.variantId")
List<Inventory> findAllByVariantIdsForUpdate(@Param("variantIds") List<UUID> variantIds);

// ⚠️ ORDER BY variantId — bắt buộc để tránh deadlock
// Nếu Thread A lock variantId=1 rồi chờ variantId=2
// mà Thread B lock variantId=2 rồi chờ variantId=1
// → Deadlock. ORDER BY đảm bảo mọi thread lock theo cùng thứ tự.
```

**SQL được generate:**
```sql
SELECT * FROM inventories
WHERE variant_id IN (?, ?)
ORDER BY variant_id
FOR UPDATE
```

---

## Khi nào dùng Pessimistic, khi nào dùng Redis DECR

| Tình huống | Giải pháp | Lý do |
|---|---|---|
| Đặt hàng thường | Pessimistic Lock (DB) | Cần transaction ACID, liên quan nhiều bảng |
| Flash sale | Redis DECR atomic | Hàng nghìn request/giây, DB không chịu được |
| Nhập kho thủ công | Optimistic Lock (version) | Ít conflict, không cần block |

---

## Scheduled Job — Tự động release reservation hết hạn

Cần có job chạy định kỳ để release những reservation quá 15 phút mà chưa thanh toán (user thoát ra giữa chừng).

```
Job: expireReservations()
Chạy: mỗi 1 phút
Logic:
  1. SELECT * FROM stock_reservations
     WHERE status = 'RESERVED' AND expires_at < NOW()
  2. Với mỗi reservation hết hạn:
     a. BEGIN TRANSACTION
     b. SELECT FOR UPDATE inventory row
     c. UPDATE reservedQty -= qty
     d. UPDATE stock_reservations SET status = 'EXPIRED'
     e. COMMIT
  3. Publish stock.released event với reason = RESERVATION_EXPIRED
```