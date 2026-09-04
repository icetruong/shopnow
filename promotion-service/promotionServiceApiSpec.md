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
  "message": "Mã giảm giá hợp lệ",
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
  "code":            "SALE10",
  "remainingGlobal": 149,
  "userUsageCount":  1
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

---

### GET /coupons/available
Lấy danh sách coupon user có thể dùng (hiển thị trang khuyến mãi).

**Header:** `Authorization: Bearer {accessToken}`

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách coupon thành công",
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
  "message": "Tạo mã giảm giá thành công",
  "data": { "couponId": "coupon-uuid-1", "code": "SALE10" }
}
```

**Side effect:** Khởi tạo Redis counter `coupon:usage:{code}` = usageLimit.

---

### PUT /admin/coupons/{couponId}
Cập nhật coupon. Partial update — gửi field nào cập nhật field đó. `code` **không đổi được** vì Redis key gắn theo code.

**Header:** `Authorization: Bearer {accessToken}` — role `ADMIN`

**Request Body** 
```json
{
  "title":          "Giảm 10% tối đa 70k",
  "discountType":   "PERCENTAGE",
  "discountValue":  10,
  "maxDiscount":    70000,
  "minOrder":       200000,
  "usageLimit":     200,
  "userLimit":      1,
  "startsAt":       "2024-01-15T00:00:00Z",
  "endsAt":         "2024-02-15T23:59:59Z",
  "applicableType": "CATEGORY",
  "applicableIds":  ["cat-uuid-1", "cat-uuid-2"],
  "isActive":       true
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Cập nhật coupon thành công",
  "data": {
    "couponId":   "coupon-uuid-1",
    "code":       "SALE10",
    "usageLimit": 200,
    "usedCount":  37,
    "remaining":  163,
    "updatedAt":  "2024-01-20T09:30:00Z"
  }
}
```

**Response 404**
```json
{ "success": false, "code": "COUPON_NOT_FOUND", "message": "Không tìm thấy coupon." }
```

**Response 400** — kèm lý do cụ thể
```json
{ "success": false, "code": "COUPON_UPDATE_INVALID", "message": "usageLimit không được nhỏ hơn số lượt đã dùng (37)." }
```

**reason values:**
```
CODE_IMMUTABLE          — body gửi "code" khác code hiện tại
USAGE_LIMIT_BELOW_USED  — usageLimit mới < usedCount
TIME_RANGE_INVALID      — endsAt <= startsAt
DISCOUNT_VALUE_INVALID  — PERCENTAGE mà value ngoài 1..100, hoặc value <= 0
APPLICABLE_IDS_REQUIRED — applicableType = CATEGORY/PRODUCT nhưng applicableIds rỗng
```

**Flow bên trong:**
```
1. Tìm coupon theo couponId → 404 COUPON_NOT_FOUND nếu không có
2. Nếu body có "code" khác code hiện tại → 400 CODE_IMMUTABLE
3. Validate field gửi lên (time range, discountValue, applicableIds…)
4. Nếu có usageLimit mới:
   a. usageLimit mới < usedCount → 400 USAGE_LIMIT_BELOW_USED
   b. delta = usageLimit mới - usageLimit cũ
   c. INCRBY coupon:usage:{code} delta   (nới/thu lượt CÒN LẠI đúng phần chênh, KHÔNG SET đè)
5. Nếu có endsAt mới → EXPIRE coupon:usage:{code} theo (endsAt mới - now) giây
6. UPDATE các cột vào DB, set updated_at = now
7. Trả coupon sau cập nhật (remaining đọc lại từ Redis)
```

**Side effect (Redis):**
- Đổi `usageLimit` → `INCRBY coupon:usage:{code}` theo `delta` (set đè sẽ làm mất phần đã trừ).
- Đổi `endsAt` → cập nhật TTL của `coupon:usage:{code}`.
- Đổi `isActive = false` → xử lý như `DELETE` (xem dưới).

> **Ghi chú schema:** bảng `coupons` hiện chỉ có `created_at`. Endpoint này cần bổ sung cột `updated_at TIMESTAMP` để trả `updatedAt`.

---

### DELETE /admin/coupons/{couponId}
Vô hiệu hóa coupon — **soft delete** (set `is_active = false`), KHÔNG xóa cứng để giữ lịch sử trong `coupon_usages`.

**Header:** `Authorization: Bearer {accessToken}` — role `ADMIN`

**Path param:** `couponId` — UUID coupon

**Response 200**
```json
{
  "success": true,
  "message": "Đã vô hiệu hóa coupon"
}
```

**Response 404**
```json
{ "success": false, "code": "COUPON_NOT_FOUND", "message": "Không tìm thấy coupon." }
```

**Response 409** — đã bị vô hiệu hóa từ trước
```json
{ "success": false, "code": "COUPON_ALREADY_INACTIVE", "message": "Coupon đã bị vô hiệu hóa." }
```

**Flow bên trong:**
```
1. Tìm coupon theo couponId → 404 COUPON_NOT_FOUND nếu không có
2. is_active đang = false → 409 COUPON_ALREADY_INACTIVE
3. UPDATE coupons SET is_active = false
4. DEL coupon:usage:{code}      (chặn validate/apply dùng tiếp ngay lập tức)
5. coupon:user:{code}:*         cứ để tự hết hạn theo TTL, không cần xóa
6. Trả message
```

**Vì sao soft delete?**
- `coupon_usages` có FK trỏ tới `coupons(id)` — xóa cứng làm vỡ ràng buộc + mất audit.
- Đơn cũ đã áp coupon này vẫn cần tra cứu lại được.

**Side effect (Redis):** `DEL coupon:usage:{code}`. Sau đó `POST /coupons/validate` trả `reason: NOT_FOUND` (do filter `is_active`), `apply` tương tự.

---

### GET /admin/coupons
Danh sách coupon kèm thống kê lượt dùng (trang quản trị). Có phân trang.

**Header:** `Authorization: Bearer {accessToken}` — role `ADMIN`

**Query params:**
```
page     = 0                       số trang, mặc định 0
size     = 20                      mặc định 20, tối đa 100
status   = ALL | ACTIVE | INACTIVE | SCHEDULED | EXPIRED   lọc, mặc định ALL
keyword  = <chuỗi>                 tìm theo code hoặc title (optional)
```

> **`status` KHÔNG phải cột trong bảng `coupons`** — nó là bí danh suy ra từ `is_active` + `starts_at` + `ends_at`.
> Filter `?status=X` dịch thành `WHERE` như sau (dùng đúng định nghĩa với field `status` ở response):
> ```
> ALL        → không thêm điều kiện
> INACTIVE   → is_active = false
> SCHEDULED  → is_active = true AND now < starts_at
> EXPIRED    → is_active = true AND now > ends_at
> ACTIVE     → is_active = true AND starts_at <= now AND now <= ends_at
> ```
> `INACTIVE` được ưu tiên: coupon vừa `is_active = false` vừa quá hạn → tính là `INACTIVE`, không phải `EXPIRED`.

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách coupon thành công",
  "data": {
    "content": [
      {
        "couponId":       "coupon-uuid-1",
        "code":           "SALE10",
        "title":          "Giảm 10% tối đa 50k",
        "discountType":   "PERCENTAGE",
        "discountValue":  10,
        "maxDiscount":    50000,
        "minOrder":       200000,
        "usageLimit":     150,
        "usedCount":      37,
        "remaining":      113,
        "userLimit":      1,
        "applicableType": "ALL",
        "startsAt":       "2024-01-15T00:00:00Z",
        "endsAt":         "2024-01-31T23:59:59Z",
        "isActive":       true,
        "status":         "ACTIVE",
        "createdAt":      "2024-01-10T08:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "statistics": {
      "totalCoupons":   42,
      "activeCoupons":  18,
      "expiredCoupons": 20,
      "totalRedeemed":  1536
    }
  }
}
```

**Field tính toán:**
```
remaining  = GET coupon:usage:{code} (Redis, realtime).
             Key không tồn tại → fallback = usageLimit - usedCount.
usedCount  = cột used_count (đồng bộ async từ Redis, có thể trễ vài giây).
status     = giá trị suy ra (KHÔNG lưu DB), tính theo thứ tự ưu tiên:
             INACTIVE  nếu is_active = false
             SCHEDULED nếu now < starts_at
             EXPIRED   nếu now > ends_at
             ACTIVE    các trường hợp còn lại
totalRedeemed = tổng used_count toàn bộ coupon (đếm coupon_usages status = APPLIED).
```

**Flow bên trong:**
```
1. Dịch status → điều kiện WHERE trên is_active/starts_at/ends_at (xem bảng trên); ghép keyword; Pageable(page, size, sort)
2. Lấy 1 page coupon từ DB
3. Gom code cả trang → MGET coupon:usage:{code}... một lần → gán remaining từng dòng
4. Tính field status cho từng dòng theo giờ hiện tại (cùng công thức với filter)
5. Tính block stats (nên cache 30–60s vì quét toàn bảng)
6. Trả theo format phân trang chuẩn: content / page / size / totalElements / totalPages (+ stats)
```

**Lưu ý hiệu năng:** KHÔNG gọi Redis `GET` trong vòng lặp từng coupon — gom `code` cả trang rồi `MGET` một lần (1 round-trip).

---

## 3. FLASH SALE — Phần quan trọng nhất

---

> ### ⚠️ Phân vai với Inventory Service
>
> | Việc | Chủ sở hữu | Chi tiết |
> |---|---|---|
> | **Định nghĩa** flash sale: variant nào, `flashPrice`, lịch `startsAt/endsAt`, `limitPerUser` | **Promotion Service** | bảng `flash_sales`, `flash_sale_items` |
> | **Counter tồn kho** flash sale (`flash:stock`), cờ `flash:active`, counter `flash:user`, **Lua script atomic chống oversell** | **Inventory Service** | Redis keys `flash:*`, bảng `flash_sale_stocks` — xem `inventoryServiceApiSpec.md` PHẦN 3 |
> | Lịch sử mua (`flash_sale_purchases`) + analytics | **Promotion Service** (consume event `flash.purchased` từ Inventory) | ghi async |
>
> → Promotion Service **KHÔNG** giữ Redis key `flash:*`, **KHÔNG** viết Lua. Các endpoint `purchase` / `rollback` / `warmup` của Promotion là **orchestration mỏng** — tra dữ liệu từ DB của mình rồi gọi REST sang Inventory Service.

---

### GET /flash-sales/active
Lấy flash sale đang diễn ra (hiển thị trang chủ).

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách flash sale thành công",
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

**Lưu ý:**
- `remaining` / `soldQty` lấy từ `flash_sale_items.sold_qty` của Promotion Service — cột này được đồng bộ **async** từ event `flash.purchased` (Inventory phát mỗi lượt mua), trễ ~1–2 giây. Số realtime tuyệt đối nằm ở Redis của Inventory; với trang chủ thì trễ vài giây là chấp nhận được.
- `remaining = total_qty - sold_qty`, `soldPercent = sold_qty / total_qty * 100` để vẽ thanh "đã bán 67%".
- `serverTime` để client đếm ngược đồng bộ, không tin giờ máy client.

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
  "message": "Mua flash sale thành công",
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

**Flow bên trong (orchestration — KHÔNG đụng Redis):**
```
1. Tra flash_sale_items theo (flashSaleId, variantId)
   → 404 FLASH_SALE_NOT_FOUND nếu không có
   → lấy flashPrice, limitPerUser
2. Gọi Inventory Service:
   POST /internal/stock/flash-sale/reserve
   { flashSaleId, variantId, orderId, userId, qty, limitPerUser }
3. Map kết quả trả về từ Inventory:
   - 200               → trả { flashPrice, remaining, reservedAt }   (gắn flashPrice từ bước 1)
   - 409 FLASH_SALE_SOLD_OUT    → passthrough 409 FLASH_SALE_SOLD_OUT
   - 409 FLASH_SALE_USER_LIMIT  → passthrough 409 FLASH_SALE_LIMIT_REACHED
   - 400 FLASH_SALE_NOT_ACTIVE  → passthrough 400 FLASH_SALE_NOT_ACTIVE
4. KHÔNG publish Kafka ở đây — Inventory phát flash.purchased sau khi DECR thành công.
   KHÔNG ghi flash_sale_purchases ở đây — làm ở consumer (xem PHẦN 5).
```

> Idempotency (Order Service retry) do **Inventory Service** xử lý theo `orderId`. Promotion Service ở bước này là stateless.

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

**Flow bên trong (orchestration):**
```
1. Gọi Inventory Service:
   POST /internal/stock/flash-sale/release
   { flashSaleId, variantId, orderId, userId, qty }
   → Inventory: INCRBY flash:stock + DECRBY flash:user (atomic)
2. Inventory trả 200 → Promotion cập nhật DB của mình NGAY (đồng bộ, không qua event):
   UPDATE flash_sale_purchases SET status = ROLLED_BACK WHERE order_id = ? AND flash_sale_id = ?
   UPDATE flash_sale_items    SET sold_qty = sold_qty - qty
3. Idempotent: bản ghi flash_sale_purchases đã ROLLED_BACK (hoặc không có) → bỏ qua, vẫn trả 200.
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

**Side effect:** chỉ ghi DB (`flash_sales` status = SCHEDULED, `flash_sale_items`). **KHÔNG** seed Redis — việc đó ở bước warmup và do Inventory Service làm.

---

### POST /admin/flash-sales/{flashSaleId}/warmup
**Warmup — nạp tồn kho flash sale vào Redis TRƯỚC khi bắt đầu.** Bắt buộc chạy trước giờ G vài phút.

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

**Flow bên trong (orchestration):**
```
1. Đọc flash_sale_items của flashSaleId từ DB Promotion
2. Gọi Inventory Service:
   POST /admin/flash-sale-stock
   {
     flashSaleId, startsAt, endsAt,
     items: [ { variantId, flashSaleQty: totalQty }, ... ]
   }
   → Inventory validate flashSaleQty <= stockQty thật + seed:
       SET flash:stock:{flashSaleId}:{variantId} = totalQty   (TTL đến endsAt)
       SET flash:active:{flashSaleId} = "1"                    (TTL đến endsAt)
     và INSERT flash_sale_stocks (audit)
3. Inventory trả 200 → Promotion: UPDATE flash_sales SET is_warmed = true
4. (Tùy chọn) publish promotion.flash_sale_starting để Notification broadcast
```

`flashPrice` và `limitPerUser` **không** gửi sang Inventory ở đây — Promotion tự giữ, truyền `limitPerUser` trong từng lần gọi `/internal/stock/flash-sale/reserve`.

**Tại sao cần warmup?** Nếu đợi request mua đầu tiên mới load DB → Redis: hàng nghìn request cùng cache-miss một lúc → **cache stampede**, DB nghẽn, flash sale sập giây đầu. Warmup trước → Redis sẵn sàng 100% khi giờ G tới.

---

## 5. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `COUPON_INVALID` | 400 | Coupon không hợp lệ (xem reason) |
| `COUPON_NOT_FOUND` | 404 | Mã / coupon không tồn tại |
| `COUPON_UPDATE_INVALID` | 400 | PUT coupon sai điều kiện (xem reason) |
| `COUPON_ALREADY_INACTIVE` | 409 | DELETE coupon đã bị vô hiệu hóa trước đó |
| `FLASH_SALE_NOT_FOUND` | 404 | Không tìm thấy flash_sale_items theo (flashSaleId, variantId) |
| `FLASH_SALE_SOLD_OUT` | 409 | Hết hàng flash sale — *passthrough từ Inventory* |
| `FLASH_SALE_LIMIT_REACHED` | 409 | User đã mua đủ giới hạn — *passthrough từ Inventory (`FLASH_SALE_USER_LIMIT`)* |
| `FLASH_SALE_NOT_ACTIVE` | 400 | Flash sale chưa bắt đầu / đã kết thúc — *passthrough từ Inventory* |

---

## 6. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role | Ghi chú |
|--------|----------|------|------|---------|
| POST | /coupons/validate | ✅ | USER | |
| GET | /coupons/available | ✅ | USER | |
| POST | /internal/coupons/apply | 🔒 Internal | — | |
| POST | /internal/coupons/rollback | 🔒 Internal | — | |
| POST | /admin/coupons | ✅ | ADMIN | |
| PUT | /admin/coupons/{id} | ✅ | ADMIN | |
| DELETE | /admin/coupons/{id} | ✅ | ADMIN | |
| GET | /admin/coupons | ✅ | ADMIN | |
| GET | /flash-sales/active | ❌ | — | đọc từ flash_sale_items.sold_qty (sync async) |
| POST | /internal/flash-sales/purchase | 🔒 Internal | — | orchestration → gọi Inventory `/internal/stock/flash-sale/reserve` |
| POST | /internal/flash-sales/rollback | 🔒 Internal | — | orchestration → gọi Inventory `/internal/stock/flash-sale/release` |
| POST | /admin/flash-sales | ✅ | ADMIN | chỉ ghi DB, không seed Redis |
| POST | /admin/flash-sales/{id}/warmup | ✅ | ADMIN | orchestration → gọi Inventory `/admin/flash-sale-stock` |

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
| flash_price | BIGINT | NOT NULL | Giá flash sale — Promotion sở hữu, truyền cho consumer |
| total_qty | INT | NOT NULL | Tổng số lượng flash — gửi sang Inventory lúc warmup |
| sold_qty | INT | NOT NULL, DEFAULT 0 | Đã bán — cập nhật async khi consume `flash.purchased` từ Inventory (không đọc Redis trực tiếp) |
| limit_per_user | INT | NOT NULL, DEFAULT 1 | Promotion giữ, truyền vào mỗi lần gọi Inventory reserve |
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

# PHẦN 3 — LUA SCRIPT & CHỐNG OVERSELL → xem Inventory Service

Toàn bộ phần Redis atomic / Lua script chống oversell của flash sale **nằm ở Inventory Service**, không phải ở đây. Xem `inventoryServiceApiSpec.md` PHẦN 3 (mục "Flash sale — Lua script atomic").

Lý do phân vai: Inventory Service đã sở hữu mọi loại tồn kho, có bảng `flash_sale_stocks` để audit/restore, và validate được `flashSaleQty <= stockQty` thật. Để nó giữ luôn counter flash sale → tránh 2 service cùng `DECR` một key (split-brain).

## Phần thuộc về Promotion Service: ghi DB async

Promotion Service **consume** event `flash.purchased` (Inventory phát mỗi lượt mua thành công) và:
```
1. INSERT flash_sale_purchases (async, idempotent theo (flash_sale_id, user_id, order_id))
2. UPDATE flash_sale_items.sold_qty += qty   (để GET /flash-sales/active hiển thị)
3. Dedup bằng key processed:event:{eventId} TTL 24h
```
→ Order Service nhận response ngay từ Inventory, không phải chờ Promotion ghi DB. Eventual consistency, trễ ~1–2s.

---

# PHẦN 4 — REDIS KEYS TỔNG HỢP

> Các key `flash:*` **thuộc Inventory Service** — không liệt kê ở đây. Xem `inventoryServiceApiSpec.md`.

| Key pattern | Type | TTL | Mục đích |
|-------------|------|-----|---------|
| `coupon:usage:{code}` | String (INT) | Đến endsAt | Lượt coupon còn lại — DECR atomic |
| `coupon:user:{code}:{userId}` | String (INT) | Đến endsAt | User đã dùng coupon mấy lần |
| `processed:event:{eventId}` | String | 24 giờ | Idempotency khi consume Kafka |

Các key `flash:stock:*`, `flash:active:*`, `flash:user:*` do **Inventory Service** quản lý.

---

# PHẦN 5 — KAFKA EVENTS

### Consume: flash.purchased
**Do Inventory Service publish** (sau khi DECR `flash:stock` thành công). Promotion Service **consume**.

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
**Promotion Service xử lý:** `flashPrice` tra từ `flash_sale_items` (payload không mang giá) → INSERT `flash_sale_purchases` (idempotent) + `flash_sale_items.sold_qty += qty`. Dedup `processed:event:{eventId}` TTL 24h.
**Consumer khác:** Analytics.

---

### Publish: promotion.flash_sale_starting
Publish trước khi flash sale bắt đầu (VD 15 phút trước) để Notification broadcast.

```json
{
  "eventId":   "uuid-v4",
  "eventType": "promotion.flash_sale_starting",
  "timestamp": "2024-12-12T00:00:00Z",
  "version":   "1.0",
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
1. Tại sao tách flash sale counter sang Inventory Service?
   → Inventory đã sở hữu mọi loại tồn kho + validate được stockQty thật
   → Nếu Promotion cũng giữ flash:stock → 2 service cùng DECR 1 key → split-brain
   → Promotion giữ "định nghĩa" (giá, lịch, limitPerUser); Inventory giữ "counter"

2. Tại sao Redis thay vì DB lock cho flash sale? (câu hỏi cho Inventory)
   → DB pessimistic lock: request xếp hàng chờ nhau → chậm, dễ timeout ở nghìn req/s
   → Redis single-thread + Lua atomic → 100k+ ops/giây, không lock

3. DECR đơn thuần vs Lua script?
   → DECR đủ nếu chỉ chống oversell
   → Lua script khi cần atomic nhiều điều kiện cùng lúc (active + user limit + stock)

4. Tại sao warmup trước?
   → Tránh cache stampede: nghìn request đầu cùng cache-miss, cùng load DB
   → Đảm bảo Redis sẵn sàng đúng giờ G

5. Consistency giữa services?
   → Redis (Inventory) là source of truth khi flash sale đang chạy
   → Promotion nhận số qua event flash.purchased → DB đồng bộ async (~1–2s)
   → Sau flash sale: Inventory reconcile Redis final → flash_sale_stocks.sold_qty
```