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

### POST /internal/stock/batch
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
      "sku":          "POLO-WHITE-S",
      "stockQty":     50,
      "reservedQty":  5,
      "availableQty": 45,
      "status":       "IN_STOCK"
    },
    {
      "variantId":    "var-uuid-2",
      "sku":          "POLO-WHITE-M",
      "stockQty":     10,
      "reservedQty":  7,
      "availableQty": 3,
      "status":       "LOW_STOCK"
    },
    {
      "variantId":    "var-uuid-3",
      "sku":          "POLO-WHITE-L",
      "stockQty":     5,
      "reservedQty":  5,
      "availableQty": 0,
      "status":       "OUT_OF_STOCK"
    }
  ]
}
```

---

### POST /internal/stock
Product Service gọi khi tạo variant mới để khởi tạo inventory record tương ứng.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "variantId": "var-uuid-1",
  "sku":       "POLO-WHITE-S",
  "stockQty":  10
}
```

**Response 201** — tạo thành công
```json
{
  "variantId":    "var-uuid-1",
  "sku":          "POLO-WHITE-S",
  "stockQty":     10,
  "reservedQty":  0,
  "availableQty": 10,
  "status":       "IN_STOCK"
}
```

**Response 409** — variant đã tồn tại
```json
{
  "success":   false,
  "errorCode": "INVENTORY_ALREADY_EXISTS",
  "message":   "Inventory cho variantId này đã tồn tại."
}
```

**Logic:**
```
1. Kiểm tra variantId chưa tồn tại trong inventories
2. Nếu đã tồn tại → trả 409
3. INSERT inventory (reservedQty = 0, soldQty = 0)
4. Trả về inventory vừa tạo
```

---

### POST /internal/stock/bulk
Product Service gọi khi tạo nhiều variant cùng lúc để khởi tạo inventory record tương ứng.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "items": [
    {
      "variantId": "var-uuid-1",
      "sku":       "POLO-WHITE-S",
      "stockQty":  10
    },
    {
      "variantId": "var-uuid-2",
      "sku":       "POLO-WHITE-M",
      "stockQty":  10
    }
  ]
}
```

**Response 201** — tạo thành công toàn bộ
```json
{
  "items": [
    {
      "variantId":    "var-uuid-1",
      "sku":          "POLO-WHITE-S",
      "stockQty":     10,
      "reservedQty":  0,
      "availableQty": 10,
      "status":       "IN_STOCK"
    },
    {
      "variantId":    "var-uuid-2",
      "sku":          "POLO-WHITE-M",
      "stockQty":     10,
      "reservedQty":  0,
      "availableQty": 10,
      "status":       "IN_STOCK"
    }
  ]
}
```

**Response 409** — một hoặc nhiều variantId đã tồn tại
```json
{
  "success":   false,
  "errorCode": "INVENTORY_ALREADY_EXISTS",
  "message":   "Một số variantId đã tồn tại trong inventory.",
  "data": ["var-uuid-1", "var-uuid-2"]
}
```

**Logic:**
```
1. Lấy toàn bộ variantId từ request
2. Query batch: tìm những variantId đã tồn tại trong inventories
3. Nếu có bất kỳ duplicate nào → trả 409 kèm danh sách conflicts (all-or-nothing, không insert gì)
4. INSERT toàn bộ inventory trong 1 transaction (reservedQty = 0, soldQty = 0)
5. Trả về danh sách inventory vừa tạo
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
```
Không publish Kafka event gì cả — Order Service gọi REST này đã biết ngay còn/hết hàng trong response 200/409.

---

### POST /internal/stock/release
Giải phóng hàng đã reserve — Order Service gọi REST đồng bộ khi thanh toán thất bại hoặc user hủy đơn (Saga compensating).

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "orderId": "order-uuid-1",
  "reason":  "PAYMENT_FAILED"
}
```

**Reason values:** `PAYMENT_FAILED` / `ORDER_CANCELLED`

> `RESERVATION_EXPIRED` **không** đi qua endpoint này — trường hợp đó do chính Inventory Service tự phát hiện qua Scheduler (`SchedulerStockReserve`, chạy mỗi phút) và tự release nội bộ, không phải Order Service gọi REST tới đây.

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
```
Không publish Kafka event gì cả — Order Service gọi REST này đã biết kết quả ngay trong response 200, không cần thông báo lại qua Kafka.

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

### POST /internal/stock/return
Cộng lại kho **sau khi đã deduct** — dùng khi hủy đơn đã ở trạng thái `CONFIRMED` (Order Service Case 3: user hủy đơn đã thanh toán + đã trừ kho thật). Đây là chiều ngược lại của `deduct`, **khác với `release`** (chỉ hoạt động trên bản ghi còn `RESERVED`, không xử lý được bản ghi đã `DEDUCTED`).

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
  "returnedAt": "2024-01-15T10:40:00Z"
}
```

**Response 404** — không tìm thấy bản ghi đã deduct cho orderId này
```json
{
  "success":   false,
  "errorCode": "RESERVATION_NOT_FOUND",
  "message":   "Không tìm thấy bản ghi đã trừ kho cho đơn hàng này."
}
```

**Logic bên trong:**
```
1. Tìm stock_reservations theo orderId (status = DEDUCTED)
2. Nếu rỗng → trả 404 RESERVATION_NOT_FOUND
3. BEGIN TRANSACTION
4. SELECT ... FOR UPDATE các inventory row liên quan
5. UPDATE stockQty += qty (cộng lại kho vật lý — ngược thao tác deduct)
6. UPDATE soldQty -= qty (bỏ khỏi đã bán)
7. INSERT vào stock_transactions (type = RETURN, qty dương, order_id, note "Hoàn kho do hủy đơn sau khi đã CONFIRMED")
8. UPDATE stock_reservations SET status = RETURNED
9. COMMIT
```
Không publish Kafka event — Order Service gọi REST này đã biết kết quả ngay trong response 200/404, giống `reserve`/`release`/`deduct`.

> **Cần bổ sung 2 enum:** `StockReservationStatus` thêm giá trị `RETURNED`; `StockTransactionType` thêm giá trị `RETURN` — hiện cả 2 chưa có, cần thêm khi implement endpoint này.

---

### POST /internal/stock/flash-sale/reserve
Reserve hàng flash sale — dùng **Redis Lua script atomic** thay vì DB lock để chịu tải cao.

> **Phân vai:** "định nghĩa" flash sale (variant nào, `flashPrice`, lịch, `limitPerUser`) do **Promotion Service** quản lý. Inventory Service chỉ giữ **counter tồn kho + chống oversell**. Promotion Service gọi endpoint này ở `POST /internal/flash-sales/purchase` của nó; `limitPerUser` được Promotion truyền vào đây.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
 {
    "flashSaleId":  "fs-uuid-1",
    "variantId":    "var-uuid-1",
    "orderId":      "order-uuid-1",
    "userId":       "user-uuid-1",
    "qty":          1,
    "limitPerUser": 1
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
{ "success": false, "errorCode": "FLASH_SALE_SOLD_OUT", "message": "Sản phẩm flash sale đã hết." }
```

**Response 409** — user vượt giới hạn
```json
{ "success": false, "errorCode": "FLASH_SALE_USER_LIMIT", "message": "Bạn đã mua đủ giới hạn trong flash sale này." }
```

**Response 400** — flash sale không active
```json
{ "success": false, "errorCode": "FLASH_SALE_NOT_ACTIVE", "message": "Flash sale chưa bắt đầu hoặc đã kết thúc." }
```

**Logic bên trong — 1 Lua script atomic** (xem PHẦN 3, mục "Flash sale — Lua script atomic"):
```
KEYS = flash:stock:{fs}:{variant} , flash:user:{fs}:{variant}:{userId} , flash:active:{fs}
ARGV = qty , limitPerUser , ttl(giây tới endsAt)

1. EXISTS flash:active → 0  ⇒ return -3  (FLASH_SALE_NOT_ACTIVE)
2. userBought + qty > limitPerUser  ⇒ return -2  (FLASH_SALE_USER_LIMIT)
3. stock < qty  ⇒ return -1  (FLASH_SALE_SOLD_OUT)
4. DECRBY stock qty ; INCRBY user qty ; EXPIRE user ttl
5. return remaining (>= 0)

Java map: -1/-2/-3 → throw tương ứng; >= 0 → 200.
```

**Idempotency (Order Service retry):** trước khi chạy Lua, `SET flash:done:{orderId}:{variantId} 1 EX ttl NX`. Nếu key đã tồn tại → không chạy Lua nữa, trả lại kết quả cũ.

**Sau khi Lua thành công:** publish Kafka `flash.purchased` (Promotion Service consume để ghi lịch sử + analytics). Xem PHẦN 2 → "Kafka Events".

---

### POST /internal/stock/flash-sale/release
Hoàn lại số lượng flash sale khi đơn bị hủy (compensating). Promotion Service gọi ở `POST /internal/flash-sales/rollback`.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "flashSaleId": "fs-uuid-1",
  "variantId":   "var-uuid-1",
  "orderId":     "order-uuid-1",
  "userId":      "user-uuid-1",
  "qty":         1
}
```

**Response 200**
```json
{ "success": true, "message": "Đã hoàn lại số lượng flash sale." }
```

**Logic bên trong — 1 Lua script atomic:**
```
KEYS = flash:stock:{fs}:{variant} , flash:user:{fs}:{variant}:{userId} , flash:done:{orderId}:{variant}

1. EXISTS flash:done → 0  ⇒ return 0   (chưa từng reserve / đã release rồi → no-op, vẫn 200)
2. INCRBY stock qty ; DECRBY user qty ; DEL flash:done
3. return stock mới
```
- Không publish Kafka — Promotion là bên gọi, biết kết quả ngay trong response 200 (giống `reserve`).
- Idempotent nhờ key `flash:done`: gọi lại lần 2 rơi vào bước 1 → no-op.

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
  "message": "Lấy danh sách tồn kho thành công.",
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
  "message": "Nhập hàng thành công.",
  "data": {
    "variantId":    "var-uuid-1",
    "previousQty":  50,
    "importedQty":  100,
    "currentQty":   150,
    "updatedAt":    "2024-01-15T14:00:00Z"
  }
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
  "message": "Điều chỉnh tồn kho thành công.",
  "data": { 
    "variantId":    "var-uuid-1",
    "previousQty":  50,
    "adjustedQty":  45,
    "difference":   -5,
    "updatedAt":    "2024-01-15T14:00:00Z"
  }
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
  "message": "Lấy lịch sử biến động tồn kho thành công.", 
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
    "size":          20,
    "totalElements": 25,
    "totalPages":    2
  }
}
```

---

## 3. FLASH SALE STOCK — Khởi tạo flash sale

---

### POST /admin/flash-sale-stock
Khởi tạo tồn kho flash sale trong Redis (**"warmup"**) — chạy trước giờ G vài phút.

> **Promotion Service gọi endpoint này** trong bước `POST /admin/flash-sales/{id}/warmup`. Admin không gọi trực tiếp (nhưng vẫn để role ADMIN + internal token cho tiện test).

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
`flashPrice` / `limitPerUser` **không** nhận ở đây — Promotion giữ, truyền `limitPerUser` trong từng lần gọi `/internal/stock/flash-sale/reserve`.

**Response 200**
```json
{
  "success": true,
  "message": "Đã khởi tạo tồn kho flash sale thành công.",
  "data": { "itemsLoaded": 2 }
}
```

**Logic:**
```
1. Validate flashSaleQty <= stockQty thực tế của từng variant → 409 INSUFFICIENT_STOCK nếu thiếu
2. SET flash:stock:{flashSaleId}:{variantId} = flashSaleQty   (TTL đến endsAt)
3. SET flash:active:{flashSaleId} = "1"                        (TTL đến endsAt)
   (hoặc: job hẹn giờ bật đúng startsAt — tránh mua sớm trong khoảng warmup→startsAt)
4. INSERT flash_sale_stocks (audit + restore nếu Redis mất)
```

> **Tại sao warmup trước:** đợi request mua đầu tiên mới nạp DB→Redis → hàng nghìn request cùng cache-miss một lúc → **cache stampede**, DB nghẽn. Warmup trước → Redis sẵn sàng 100% khi giờ G tới.

---

## 4. KAFKA EVENTS — Consume

Inventory Service **không consume event nào cả** — reserve/release/deduct đều do Order Service gọi REST đồng bộ trực tiếp (`POST /internal/stock/reserve|release|deduct`), không qua Kafka. Inventory Service chỉ **publish**: `stock.released` (khi tự phát hiện hết hạn), `stock.low_warning`, và `flash.purchased` (mỗi lượt mua flash sale thành công → Promotion Service consume). Xem mục "Kafka Events" ở PHẦN 2.

---

## 5. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `INSUFFICIENT_STOCK` | 409 | Không đủ hàng để reserve (kể cả warmup flashSaleQty > stockQty) |
| `FLASH_SALE_SOLD_OUT` | 409 | Hết hàng flash sale |
| `FLASH_SALE_USER_LIMIT` | 409 | User đã mua đủ `limitPerUser` |
| `FLASH_SALE_NOT_ACTIVE` | 400 | Flash sale chưa bắt đầu / đã kết thúc (key `flash:active` không có) |
| `INVENTORY_NOT_FOUND` | 404 | Không tìm thấy inventory theo variantId |
| `RESERVATION_NOT_FOUND` | 404 | Không tìm thấy reservation theo orderId |
| `STOCK_NOT_FOUND` | 404 | Không tìm thấy variant trong kho |
| `INVALID_QTY` | 400 | qty phải > 0 |

---

## 6. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| GET | /internal/stock/{variantId} | 🔒 Internal | — |
| POST | /internal/stock/batch | 🔒 Internal | — |
| POST | /internal/stock | 🔒 Internal | — |
| POST | /internal/stock/bulk | 🔒 Internal | — |
| POST | /internal/stock/reserve | 🔒 Internal | — |
| POST | /internal/stock/release | 🔒 Internal | — |
| POST | /internal/stock/deduct | 🔒 Internal | — |
| POST | /internal/stock/return | 🔒 Internal | — |
| POST | /internal/stock/flash-sale/reserve | 🔒 Internal | — |
| POST | /internal/stock/flash-sale/release | 🔒 Internal | — |
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
| `flash:stock:{flashSaleId}:{variantId}` | INT (số lượng còn) | Đến endsAt | Counter tồn kho — DECRBY trong Lua |
| `flash:active:{flashSaleId}` | `"1"` | Đến endsAt | Cờ flash sale còn chạy |
| `flash:user:{flashSaleId}:{variantId}:{userId}` | **INT (số đã mua)** | Đến endsAt | So với `limitPerUser` trong Lua — **counter**, không phải `"1"` |
| `flash:done:{orderId}:{variantId}` | `"1"` | Đến endsAt | Idempotency reserve/release theo đơn hàng |

---

## Kafka Events

> `stock.reserved` đã bỏ hoàn toàn — reserve giờ là REST đồng bộ (`POST /internal/stock/reserve`), Order Service biết kết quả ngay trong response, không cần event báo lại.

### Publish: stock.released
Chỉ publish **đúng 1 trường hợp**: `SchedulerStockReserve` (chạy mỗi phút) tự phát hiện reservation quá hạn 15 phút chưa thanh toán và tự release — đây là hành động Inventory Service **tự chủ động phát hiện**, không do ai gọi REST, nên Order Service không có cách nào khác để biết ngoài lắng nghe event này.

Khi Order Service tự gọi REST `POST /internal/stock/release` (do thanh toán fail hoặc user hủy đơn), **không** publish event này nữa — Order Service đã biết kết quả ngay trong response 200 rồi.

```json
{
  "eventId":   "uuid-v4",
  "eventType": "stock.released",
  "timestamp": "2024-01-15T10:35:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":    "order-uuid-1",
    "reason":     "RESERVATION_EXPIRED",
    "releasedAt": "2024-01-15T10:35:00Z",
    "items": [
      { "variantId": "var-uuid-1", "qty": 2 },
      { "variantId": "var-uuid-2", "qty": 1 }
    ]
  }
}
```
**Consumer:** Order Service (cập nhật order status = CANCELLED, reason RESERVATION_EXPIRED — payment coi như bị bỏ dở vì user không thanh toán kịp trong 15 phút)

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

### Publish: flash.purchased
Phát sau khi Lua `reserve` trả `>= 0` (đã DECR `flash:stock` thành công).

```json
{
  "eventId":   "uuid-v4",
  "eventType": "flash.purchased",
  "timestamp": "2024-12-12T00:15:30Z",
  "version":   "1.0",
  "payload": {
    "flashSaleId": "fs-uuid-1",
    "variantId":   "var-uuid-1",
    "userId":      "user-uuid-1",
    "orderId":     "order-uuid-1",
    "qty":         1
  }
}
```
**Consumer:** Promotion Service (ghi `flash_sale_purchases` async + `flash_sale_items.sold_qty += qty`; `flashPrice` Promotion tự tra từ bảng của nó), Analytics.
> `release` (đơn bị hủy) **không** phát event — Promotion là bên gọi REST, biết kết quả ngay trong response 200.

---

---

# PHẦN 3 — CHỐNG OVERSELL: PESSIMISTIC LOCK & LUA SCRIPT

Đây là phần quan trọng nhất của Inventory Service. 2 cơ chế:
- **Đơn hàng thường** → Pessimistic Lock (DB `SELECT FOR UPDATE`).
- **Flash sale** → Redis Lua script atomic (mục "Flash sale — Lua script atomic" bên dưới).

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

## Khi nào dùng Pessimistic, khi nào dùng Redis

| Tình huống | Giải pháp | Lý do |
|---|---|---|
| Đặt hàng thường | Pessimistic Lock (DB, `SELECT FOR UPDATE`) | Cần transaction ACID, liên quan nhiều bảng |
| **Flash sale** | **Redis Lua script atomic** | Hàng nghìn request/giây, DB lock không chịu được |
| Nhập kho thủ công | Optimistic Lock (version) | Ít conflict, không cần block |

---

## Flash sale — Lua script atomic

Flash sale **không dùng DB lock** — request xếp hàng chờ lock trên 1 row ở 5000 req/s là chết. Dùng Redis: single-thread + chạy trọn 1 Lua script như 1 lệnh không chia cắt.

**Vì sao không `DECR` đơn thuần?** `DECR` đủ để chống oversell (chỉ 1 điều kiện: còn hàng). Nhưng flash sale còn phải check `limitPerUser` **cùng lúc** với trừ stock. Tách thành `GET user` → `if` → `DECR stock` → `INCR user` (4 lệnh rời) thì 1 user bắn 10 request song song có thể lách qua bước check trước khi bước tăng-counter kịp chạy. Gộp cả cụm vào 1 Lua → hết khe hở.

**Script `reserve` (dùng ở `POST /internal/stock/flash-sale/reserve`):**

```lua
-- KEYS[1] = flash:stock:{flashSaleId}:{variantId}
-- KEYS[2] = flash:user:{flashSaleId}:{variantId}:{userId}
-- KEYS[3] = flash:active:{flashSaleId}
-- ARGV[1] = qty
-- ARGV[2] = limitPerUser
-- ARGV[3] = TTL giây cho key user (= số giây tới endsAt, Java tính rồi truyền vào)

if redis.call('EXISTS', KEYS[3]) == 0 then
    return -3                                              -- flash sale không active
end

local userBought = tonumber(redis.call('GET', KEYS[2]) or '0')
if userBought + tonumber(ARGV[1]) > tonumber(ARGV[2]) then
    return -2                                              -- vượt limitPerUser
end

local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock < tonumber(ARGV[1]) then
    return -1                                              -- hết hàng
end

redis.call('DECRBY', KEYS[1], ARGV[1])
redis.call('INCRBY', KEYS[2], ARGV[1])
redis.call('EXPIRE', KEYS[2], ARGV[3])

return tonumber(redis.call('GET', KEYS[1]))                -- >= 0 : remaining
```

| Trả về | Nghĩa | Java |
|---|---|---|
| `>= 0` | mua được, đây là `remaining` | 200 + publish `flash.purchased` |
| `-1` | hết hàng | 409 `FLASH_SALE_SOLD_OUT` |
| `-2` | vượt limit user | 409 `FLASH_SALE_USER_LIMIT` |
| `-3` | flash sale không active | 400 `FLASH_SALE_NOT_ACTIVE` |

**Script `release` (dùng ở `POST /internal/stock/flash-sale/release`):**

```lua
-- KEYS[1] = flash:stock:{fs}:{variant}
-- KEYS[2] = flash:user:{fs}:{variant}:{userId}
-- KEYS[3] = flash:done:{orderId}:{variant}
-- ARGV[1] = qty

if redis.call('EXISTS', KEYS[3]) == 0 then
    return 0                                               -- chưa reserve / đã release → no-op
end
redis.call('INCRBY', KEYS[1], ARGV[1])
redis.call('DECRBY', KEYS[2], ARGV[1])
redis.call('DEL', KEYS[3])
return tonumber(redis.call('GET', KEYS[1]))
```

**Gọi từ Spring:**
```java
DefaultRedisScript<Long> script = new DefaultRedisScript<>();
script.setLocation(new ClassPathResource("redis/flash_reserve.lua"));
script.setResultType(Long.class);

Long r = redisTemplate.execute(script,
        List.of(stockKey, userKey, activeKey),
        String.valueOf(qty), String.valueOf(limitPerUser), String.valueOf(ttlSeconds));
```

**Idempotency:** trước khi chạy `reserve`, `SET flash:done:{orderId}:{variantId} 1 EX ttl NX`. Key đã tồn tại → Order Service retry → không chạy Lua, trả kết quả cũ.

**Ghi DB async:** sau khi Lua trả `>= 0` → publish `flash.purchased` → Promotion Service consume ghi lịch sử. Job định kỳ sync `flash:stock` → `flash_sale_stocks.sold_qty`. Redis chết giữa chừng → warmup lại từ `flash_sale_stocks`; bật AOF để recover phần delta chưa sync.

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