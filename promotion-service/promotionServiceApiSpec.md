# Promotion Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8091/api/v1
```

## Vai trò
Promotion Service quản lý mã giảm giá (coupon), flash sale, và giới hạn số lượng. Đây là service dùng **Redis atomic operations** nhiều nhất — vì flash sale phải chịu tải cực cao (hàng nghìn request/giây) mà không được oversell.

## Tại sao service này quan trọng
Flash sale là bài toán kinh điển về **concurrency + high throughput**. Xử lý đúng thể hiện bạn hiểu sâu về race condition, atomic operation, và cách chọn công cụ phù hợp (Redis vs DB lock).

---

# PHẦN 1 — API ENDPOINTS

---

## 1. COUPON — Mã giảm giá

---

### POST /coupons/validate
Kiểm tra mã giảm giá có hợp lệ không (gọi ở bước checkout).

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "code":       "SALE10",
  "userId":     "user-uuid-1",
  "orderTotal": 498000,
  "items": [
    { "productId": "prod-uuid-1", "categoryId": "cat-uuid-2", "qty": 2, "price": 249000 }
  ]
}
```

**Response 200** — hợp lệ
```json
{
  "success": true,
  "data": {
    "code":          "SALE10",
    "discountType":  "PERCENTAGE",
    "discountValue": 10,
    "discountAmount":49800,
    "maxDiscount":   50000,
    "finalDiscount": 49800,
    "isValid":       true
  }
}
```

**Response 400** — không hợp lệ (kèm lý do cụ thể)
```json
{
  "success": false,
  "code":    "COUPON_INVALID",
  "message": "Mã giảm giá đã hết lượt sử dụng.",
  "reason":  "USAGE_LIMIT_REACHED"
}
```

**reason values:**
```
NOT_FOUND            — mã không tồn tại
EXPIRED              — hết hạn
NOT_STARTED          — chưa đến thời gian áp dụng
USAGE_LIMIT_REACHED  — hết lượt dùng toàn hệ thống
USER_LIMIT_REACHED   — user đã dùng hết lượt
MIN_ORDER_NOT_MET    — chưa đạt giá trị đơn tối thiểu
NOT_APPLICABLE       — không áp dụng cho sản phẩm trong giỏ
```

**Flow bên trong:**
```
1. Tìm coupon theo code
2. Check thời gian: startsAt <= now <= endsAt
3. Check usage limit toàn hệ thống (Redis counter)
4. Check user limit (user này đã dùng mấy lần)
5. Check min order value
6. Check áp dụng cho category/product nào không
7. Tính discount, áp maxDiscount nếu là %
8. Trả kết quả (CHƯA trừ lượt — chỉ validate)
```

---

### POST /internal/coupons/apply
Order Service gọi khi đơn hàng đã tạo thành công — trừ lượt sử dụng coupon.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "code":    "SALE10",
  "userId":  "user-uuid-1",
  "orderId": "order-uuid-1"
}
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "code":            "SALE10",
    "remainingGlobal": 149,
    "userUsageCount":  1
  }
}
```

**Flow:** Dùng Redis atomic DECR trừ lượt global + INCR lượt user. Lưu vào bảng coupon_usages.

---

### POST /internal/coupons/rollback
Order Service gọi khi đơn bị hủy — hoàn lại lượt coupon (compensating).

**Request Body**
```json
{
  "code":    "SALE10",
  "userId":  "user-uuid-1",
  "orderId": "order-uuid-1"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã hoàn lại lượt sử dụng coupon."
}
```

---

### GET /coupons/available
Lấy danh sách coupon user có thể dùng (hiển thị trang khuyến mãi).

**Header:** `Authorization: Bearer {accessToken}`

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "code":          "SALE10",
      "title":         "Giảm 10% tối đa 50k",
      "discountType":  "PERCENTAGE",
      "discountValue": 10,
      "maxDiscount":   50000,
      "minOrder":      200000,
      "endsAt":        "2024-01-31T23:59:59Z",
      "canUse":        true
    },
    {
      "code":          "FREESHIP",
      "title":         "Miễn phí vận chuyển",
      "discountType":  "FREESHIP",
      "minOrder":      150000,
      "endsAt":        "2024-01-20T23:59:59Z",
      "canUse":        true
    }
  ]
}
```

---

## 2. ADMIN COUPON

---

### POST /admin/coupons
Tạo mã giảm giá.

**Request Body**
```json
{
  "code":         "SALE10",
  "title":        "Giảm 10% tối đa 50k",
  "discountType": "PERCENTAGE",
  "discountValue":10,
  "maxDiscount":  50000,
  "minOrder":     200000,
  "usageLimit":   150,
  "userLimit":    1,
  "startsAt":     "2024-01-15T00:00:00Z",
  "endsAt":       "2024-01-31T23:59:59Z",
  "applicableType":"ALL",
  "applicableIds": []
}
```

**discountType:** `PERCENTAGE` / `FIXED_AMOUNT` / `FREESHIP`
**applicableType:** `ALL` / `CATEGORY` / `PRODUCT`
**usageLimit:** tổng lượt dùng toàn hệ thống
**userLimit:** mỗi user dùng tối đa mấy lần

**Response 201**
```json
{
  "success": true,
  "data": { "couponId": "coupon-uuid-1", "code": "SALE10" }
}
```

**Side effect:** Khởi tạo Redis counter `coupon:usage:{code}` = usageLimit.

---

### PUT /admin/coupons/{couponId}
Cập nhật coupon.

### DELETE /admin/coupons/{couponId}
Vô hiệu hóa coupon.

### GET /admin/coupons
Danh sách coupon + thống kê lượt dùng.

---

## 3. FLASH SALE — Phần quan trọng nhất

---

### GET /flash-sales/active
Lấy flash sale đang diễn ra (hiển thị trang chủ).

**Response 200**
```json
{
  "success": true,
  "data": {
    "flashSaleId": "fs-uuid-1",
    "title":       "Flash Sale 12.12",
    "startsAt":    "2024-12-12T00:00:00Z",
    "endsAt":      "2024-12-12T02:00:00Z",
    "serverTime":  "2024-12-12T00:15:30Z",
    "items": [
      {
        "flashItemId":  "fi-uuid-1",
        "productId":    "prod-uuid-1",
        "variantId":    "var-uuid-1",
        "productName":  "Áo Polo Nam Basic",
        "thumbnail":    "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "originalPrice":249000,
        "flashPrice":   149000,
        "discountPct":  40,
        "totalQty":     100,
        "soldQty":      67,
        "remaining":    33,
        "soldPercent":  67,
        "limitPerUser": 1
      }
    ]
  }
}
```

**Lưu ý:** `remaining` lấy từ Redis (realtime), `soldPercent` để hiển thị thanh "đã bán 67%".

---

### POST /internal/flash-sales/purchase
**ĐÂY LÀ ENDPOINT QUAN TRỌNG NHẤT.** Order Service gọi khi user mua sản phẩm flash sale. Dùng Redis atomic để chống oversell.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "flashSaleId": "fs-uuid-1",
  "flashItemId": "fi-uuid-1",
  "variantId":   "var-uuid-1",
  "userId":      "user-uuid-1",
  "orderId":     "order-uuid-1",
  "qty":         1
}
```

**Response 200** — mua được
```json
{
  "success": true,
  "data": {
    "flashPrice": 149000,
    "remaining":  32,
    "reservedAt": "2024-12-12T00:15:30Z"
  }
}
```

**Response 409** — hết hàng
```json
{
  "success": false,
  "code":    "FLASH_SALE_SOLD_OUT",
  "message": "Sản phẩm flash sale đã hết!"
}
```

**Response 409** — user đã mua rồi
```json
{
  "success": false,
  "code":    "FLASH_SALE_LIMIT_REACHED",
  "message": "Mỗi người chỉ được mua 1 sản phẩm trong flash sale này."
}
```

**Flow — xem chi tiết phần 3 (Lua script).**

---

### POST /internal/flash-sales/rollback
Hoàn lại số lượng flash sale khi đơn bị hủy (compensating).

**Request Body**
```json
{
  "flashSaleId": "fs-uuid-1",
  "variantId":   "var-uuid-1",
  "userId":      "user-uuid-1",
  "orderId":     "order-uuid-1",
  "qty":         1
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã hoàn lại số lượng flash sale."
}
```

---

## 4. ADMIN FLASH SALE

---

### POST /admin/flash-sales
Tạo flash sale mới.

**Request Body**
```json
{
  "title":    "Flash Sale 12.12",
  "startsAt": "2024-12-12T00:00:00Z",
  "endsAt":   "2024-12-12T02:00:00Z",
  "items": [
    {
      "productId":    "prod-uuid-1",
      "variantId":    "var-uuid-1",
      "flashPrice":   149000,
      "totalQty":     100,
      "limitPerUser": 1
    }
  ]
}
```

**Response 201**
```json
{
  "success": true,
  "data": { "flashSaleId": "fs-uuid-1" }
}
```

---

### POST /admin/flash-sales/{flashSaleId}/warmup
**Warmup — nạp data flash sale vào Redis TRƯỚC khi bắt đầu.** Bắt buộc chạy trước giờ G vài phút.

**Response 200**
```json
{
  "success": true,
  "message": "Đã nạp flash sale vào Redis. Sẵn sàng!",
  "data": {
    "itemsLoaded": 5
  }
}
```

**Flow:**
```
1. Với mỗi flash item:
   SET flash:stock:{flashSaleId}:{variantId} = totalQty
   SET flash:price:{flashSaleId}:{variantId} = flashPrice
   SET flash:limit:{flashSaleId}:{variantId} = limitPerUser
2. SET flash:active:{flashSaleId} = "1" (TTL đến endsAt)
3. Validate totalQty <= stock thực tế (gọi Inventory Service)
```

**Tại sao cần warmup?** Nếu đợi request đầu tiên mới load từ DB vào Redis → request đó chậm + có thể nhiều request cùng load → cache stampede. Warmup trước tránh hoàn toàn.

---

## 5. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `COUPON_INVALID` | 400 | Coupon không hợp lệ (xem reason) |
| `COUPON_NOT_FOUND` | 404 | Mã không tồn tại |
| `FLASH_SALE_SOLD_OUT` | 409 | Hết hàng flash sale |
| `FLASH_SALE_LIMIT_REACHED` | 409 | User đã mua đủ giới hạn |
| `FLASH_SALE_NOT_ACTIVE` | 400 | Flash sale chưa bắt đầu / đã kết thúc |
| `FLASH_SALE_NOT_WARMED` | 500 | Chưa warmup Redis |

---

## 6. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| POST | /coupons/validate | ✅ | USER |
| GET | /coupons/available | ✅ | USER |
| POST | /internal/coupons/apply | 🔒 Internal | — |
| POST | /internal/coupons/rollback | 🔒 Internal | — |
| POST | /admin/coupons | ✅ | ADMIN |
| PUT | /admin/coupons/{id} | ✅ | ADMIN |
| DELETE | /admin/coupons/{id} | ✅ | ADMIN |
| GET | /admin/coupons | ✅ | ADMIN |
| GET | /flash-sales/active | ❌ | — |
| POST | /internal/flash-sales/purchase | 🔒 Internal | — |
| POST | /internal/flash-sales/rollback | 🔒 Internal | — |
| POST | /admin/flash-sales | ✅ | ADMIN |
| POST | /admin/flash-sales/{id}/warmup | ✅ | ADMIN |

---

---

# PHẦN 2 — DATABASE SCHEMA

---

## Bảng: coupons

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| code | VARCHAR(50) | NOT NULL, UNIQUE | Mã coupon (uppercase) |
| title | VARCHAR(255) | NOT NULL | |
| discount_type | VARCHAR(20) | NOT NULL | PERCENTAGE / FIXED_AMOUNT / FREESHIP |
| discount_value | BIGINT | NOT NULL | 10 (%) hoặc 50000 (VND) |
| max_discount | BIGINT | NULLABLE | Giảm tối đa (cho %) |
| min_order | BIGINT | NOT NULL, DEFAULT 0 | Giá trị đơn tối thiểu |
| usage_limit | INT | NOT NULL | Tổng lượt dùng |
| used_count | INT | NOT NULL, DEFAULT 0 | Đã dùng (đồng bộ từ Redis) |
| user_limit | INT | NOT NULL, DEFAULT 1 | Mỗi user tối đa mấy lần |
| applicable_type | VARCHAR(20) | NOT NULL, DEFAULT 'ALL' | ALL / CATEGORY / PRODUCT |
| applicable_ids | JSONB | NULLABLE | Mảng categoryId/productId áp dụng |
| starts_at | TIMESTAMP | NOT NULL | |
| ends_at | TIMESTAMP | NOT NULL | |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_coupons_code ON coupons(code);
CREATE INDEX idx_coupons_active_time ON coupons(is_active, starts_at, ends_at);
```

---

## Bảng: coupon_usages

Track ai đã dùng coupon nào (để check user limit + rollback).

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| coupon_id | UUID | NOT NULL, FK → coupons(id) | |
| coupon_code | VARCHAR(50) | NOT NULL | |
| user_id | UUID | NOT NULL | |
| order_id | UUID | NOT NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'APPLIED' | APPLIED / ROLLED_BACK |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_coupon_usages_coupon_user ON coupon_usages(coupon_id, user_id);
CREATE UNIQUE INDEX idx_coupon_usages_order ON coupon_usages(order_id, coupon_id);
```

---

## Bảng: flash_sales

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| title | VARCHAR(255) | NOT NULL | |
| starts_at | TIMESTAMP | NOT NULL | |
| ends_at | TIMESTAMP | NOT NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'SCHEDULED' | SCHEDULED / ACTIVE / ENDED |
| is_warmed | BOOLEAN | NOT NULL, DEFAULT FALSE | Đã nạp Redis chưa |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_flash_sales_time ON flash_sales(starts_at, ends_at);
CREATE INDEX idx_flash_sales_status ON flash_sales(status);
```

---

## Bảng: flash_sale_items

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| flash_sale_id | UUID | NOT NULL, FK → flash_sales(id) ON DELETE CASCADE | |
| product_id | UUID | NOT NULL | |
| variant_id | UUID | NOT NULL | |
| flash_price | BIGINT | NOT NULL | Giá flash sale |
| total_qty | INT | NOT NULL | Tổng số lượng flash |
| sold_qty | INT | NOT NULL, DEFAULT 0 | Đã bán (đồng bộ từ Redis) |
| limit_per_user | INT | NOT NULL, DEFAULT 1 | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_flash_items_flash_sale_id ON flash_sale_items(flash_sale_id);
CREATE UNIQUE INDEX idx_flash_items_unique ON flash_sale_items(flash_sale_id, variant_id);
```

---

## Bảng: flash_sale_purchases

Lưu lại lịch sử mua flash sale (ghi async từ Kafka, không block).

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| flash_sale_id | UUID | NOT NULL | |
| variant_id | UUID | NOT NULL | |
| user_id | UUID | NOT NULL | |
| order_id | UUID | NOT NULL | |
| qty | INT | NOT NULL | |
| flash_price | BIGINT | NOT NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PURCHASED' | PURCHASED / ROLLED_BACK |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_flash_purchases_unique ON flash_sale_purchases(flash_sale_id, user_id, order_id);
```

---

# PHẦN 3 — REDIS ATOMIC & LUA SCRIPT (điểm cốt lõi)

---

## Vấn đề race condition

```
Kho flash sale còn 1 cái. User A và User B cùng mua lúc 0h00m00s.

Nếu dùng GET rồi SET (2 lệnh riêng):
  A: GET remaining = 1  ✅
  B: GET remaining = 1  ✅  (chưa ai trừ)
  A: SET remaining = 0  → bán cho A
  B: SET remaining = 0  → bán cho B
  → OVERSELL! Bán 2 cái nhưng chỉ có 1 💀
```

---

## Giải pháp cơ bản: DECR atomic

```
DECR là atomic — Redis single-thread, không 2 lệnh chạy song song:

  A: DECR flash:stock → trả về 0  → còn 0, bán cho A ✅
  B: DECR flash:stock → trả về -1 → âm rồi, INCR trả lại → báo hết ✅

Không bao giờ oversell vì DECR đảm bảo mỗi lệnh chạy tuần tự.
```

**Nhưng DECR đơn thuần chưa đủ** — vì còn phải check `limit_per_user` (mỗi user chỉ mua 1). Cần làm 2 việc atomic cùng lúc: check user chưa mua + trừ stock. Đây là lúc cần **Lua script**.

---

## Lua Script — atomic hoàn chỉnh

Redis chạy toàn bộ Lua script atomic (không lệnh nào xen vào giữa). Script này làm 3 việc trong 1 lần:

```lua
-- KEYS[1] = flash:stock:{flashSaleId}:{variantId}
-- KEYS[2] = flash:user:{flashSaleId}:{variantId}:{userId}
-- KEYS[3] = flash:active:{flashSaleId}
-- ARGV[1] = qty muốn mua
-- ARGV[2] = limitPerUser
-- ARGV[3] = TTL cho user key (thời gian còn lại của flash sale, giây)

-- 1. Check flash sale còn active không
if redis.call('EXISTS', KEYS[3]) == 0 then
    return -3   -- flash sale không active
end

-- 2. Check user đã mua bao nhiêu
local userBought = tonumber(redis.call('GET', KEYS[2]) or '0')
if userBought + tonumber(ARGV[1]) > tonumber(ARGV[2]) then
    return -2   -- vượt limit per user
end

-- 3. Check stock còn đủ không
local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock < tonumber(ARGV[1]) then
    return -1   -- hết hàng
end

-- 4. Đủ điều kiện → trừ stock + tăng số lượng user đã mua
redis.call('DECRBY', KEYS[1], ARGV[1])
redis.call('INCRBY', KEYS[2], ARGV[1])
redis.call('EXPIRE', KEYS[2], ARGV[3])

-- 5. Trả về số lượng còn lại
return redis.call('GET', KEYS[1])
```

**Kết quả trả về:**
```
>= 0  → mua thành công, đây là số lượng còn lại
-1    → hết hàng
-2    → user vượt giới hạn
-3    → flash sale không active
```

**Tại sao Lua script?**
- Toàn bộ 5 bước chạy **atomic** — không request nào xen vào giữa
- Nếu tách thành nhiều lệnh Redis riêng → vẫn có race condition giữa các lệnh
- 1 round-trip network duy nhất → nhanh

---

## Java gọi Lua script

```
Trong Spring, dùng RedisTemplate.execute() với DefaultRedisScript:

Long result = redisTemplate.execute(
    flashSaleScript,           // Lua script đã load
    Arrays.asList(stockKey, userKey, activeKey),  // KEYS
    qty, limitPerUser, ttl     // ARGV
);

// Map kết quả:
if (result == -1) → throw SoldOutException
if (result == -2) → throw UserLimitException
if (result == -3) → throw NotActiveException
else → thành công, result = remaining
```

---

## Ghi DB async — không block user

```
Sau khi Redis trừ stock thành công:
  1. Trả response cho user NGAY (họ thấy "mua thành công")
  2. Publish Kafka event flash.purchased
  3. Consumer ghi vào flash_sale_purchases (async)
  4. Định kỳ đồng bộ sold_qty từ Redis về DB

→ User không phải chờ ghi DB. Redis là nguồn chính xác realtime,
  DB chỉ để lưu lịch sử + audit.
```

---

## Rollback khi đơn bị hủy

```
Compensating khi order flash sale bị hủy:
  1. INCRBY flash:stock:{...} qty     (trả lại hàng)
  2. DECRBY flash:user:{...} qty      (giảm số user đã mua)
  3. UPDATE flash_sale_purchases status = ROLLED_BACK

Cũng nên dùng Lua script để 2 lệnh INCRBY + DECRBY atomic.
```

---

# PHẦN 4 — REDIS KEYS TỔNG HỢP

| Key pattern | Type | TTL | Mục đích |
|-------------|------|-----|---------|
| `flash:stock:{flashSaleId}:{variantId}` | String (INT) | Đến endsAt | Số lượng còn lại — DECR atomic |
| `flash:user:{flashSaleId}:{variantId}:{userId}` | String (INT) | Đến endsAt | User đã mua bao nhiêu |
| `flash:price:{flashSaleId}:{variantId}` | String | Đến endsAt | Giá flash sale |
| `flash:limit:{flashSaleId}:{variantId}` | String | Đến endsAt | Giới hạn per user |
| `flash:active:{flashSaleId}` | String | Đến endsAt | Flag flash sale đang chạy |
| `coupon:usage:{code}` | String (INT) | Đến endsAt | Lượt còn lại — DECR atomic |
| `coupon:user:{code}:{userId}` | String (INT) | Đến endsAt | User đã dùng mấy lần |
| `processed:event:{eventId}` | String | 24 giờ | Idempotency |

---

# PHẦN 5 — KAFKA EVENTS

### Publish: flash.purchased
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
    "qty":         1,
    "flashPrice":  149000
  }
}
```
**Consumer:** Chính Promotion Service (ghi flash_sale_purchases async), Analytics

---

### Publish: promotion.flash_sale_starting
Publish trước khi flash sale bắt đầu (VD 15 phút trước) để Notification broadcast.

```json
{
  "eventId":   "uuid-v4",
  "eventType": "promotion.flash_sale_starting",
  "payload": {
    "flashSaleId": "fs-uuid-1",
    "title":       "Flash Sale 12.12",
    "startsAt":    "2024-12-12T00:00:00Z"
  }
}
```
**Consumer:** Notification Service (push "Flash sale sắp bắt đầu!")

---

# PHẦN 6 — CÁC ĐIỂM QUAN TRỌNG KHI PHỎNG VẤN

```
1. Tại sao dùng Redis thay vì DB lock cho flash sale?
   → DB pessimistic lock: các request xếp hàng chờ nhau → chậm, dễ timeout
   → Redis single-thread + atomic ops → xử lý 100k+ ops/giây, không lock

2. DECR đơn thuần vs Lua script?
   → DECR đủ nếu chỉ cần chống oversell
   → Lua script khi cần check nhiều điều kiện atomic (stock + user limit + active)

3. Làm sao không mất data khi Redis chết?
   → DB là backup (flash_sale_items lưu total + sold)
   → Redis warmup lại từ DB
   → Redis persistence (RDB/AOF) bật để recover

4. Tại sao warmup trước?
   → Tránh cache stampede: nhiều request đầu tiên cùng load DB
   → Đảm bảo Redis sẵn sàng đúng giờ G

5. Consistency giữa Redis và DB?
   → Redis là source of truth khi flash sale đang chạy (realtime)
   → DB đồng bộ async, chấp nhận eventual consistency
   → Sau flash sale: reconcile Redis final → DB
```