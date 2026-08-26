# Order Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8085/api/v1
```

## Vai trò
Order Service là **Saga orchestrator/participant** trung tâm của luồng đặt hàng. Nó điều phối order qua nhiều service bằng Kafka events và xử lý compensating khi có lỗi.

---

# PHẦN 1 — API ENDPOINTS

---

## 1. ORDER — Đặt hàng & quản lý đơn

---

### POST /orders
Tạo đơn hàng mới từ checkout token. Đây là điểm khởi đầu của Saga.

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "checkoutToken": "checkout-token-uuid",
  "addressId":     "addr-uuid-1",
  "paymentMethod": "VNPAY",
  "note":          "Giao giờ hành chính"
}
```

**paymentMethod values:** `VNPAY` / `MOMO` / `COD` (thanh toán khi nhận hàng)

**Response 201** — order được tạo, Saga bắt đầu
```json
{
  "success": true,
  "message": "Đặt hàng thành công",
  "data": {
    "orderId":     "order-uuid-1",
    "orderCode":   "SN240115001",
    "status":      "PENDING",
    "totalAmount": 448200,
    "paymentMethod":"VNPAY",
    "paymentUrl":  "https://sandbox.vnpayment.vn/...",
    "createdAt":   "2024-01-15T10:30:00Z"
  }
}
```

**Lưu ý:**
- Nếu `paymentMethod = VNPAY/MOMO` → trả về `paymentUrl` để redirect user sang trang thanh toán
- Nếu `paymentMethod = COD` → bỏ qua bước charge, đi thẳng đến confirm

**Response 409** — checkout token hết hạn
```json
{
  "success": false,
  "code":    "CHECKOUT_TOKEN_EXPIRED",
  "message": "Phiên thanh toán hết hạn, vui lòng đặt lại."
}
```

**Flow bên trong (bắt đầu Saga):**
```
1. Gọi Cart Service: GET /internal/cart/checkout/{checkoutToken}
   → lấy cart data đã validate
2. Gọi User Service: GET /internal/users/{userId}/addresses/{addressId}
   → lấy đúng địa chỉ giao hàng user đã chọn (addressId lấy từ request POST /orders)
3. BEGIN TRANSACTION (local)
   a. INSERT order (status = PENDING, payment_status = UNPAID)
   b. INSERT order_items (snapshot giá, tên, variant lúc đặt)
   c. INSERT order_shipping_address (snapshot địa chỉ)
4. Gọi Inventory Service (REST đồng bộ, X-Internal-Token):
   POST /internal/stock/reserve { orderId, items: [{variantId, qty}] }
   → Còn hàng: nhận về reservedAt, expiresAt
   → Hết hàng (409 INSUFFICIENT_STOCK) → ROLLBACK bước 3 (chưa commit gì cả),
     trả lỗi OUT_OF_STOCK cho client NGAY trong response này, KHÔNG tạo payment,
     KHÔNG publish event gì cả
5. INSERT saga_state (status = STARTED, step = STOCK_RESERVED,
   completed_steps = ["ORDER_CREATED", "STOCK_RESERVED"])
   COMMIT
6. Gọi Payment Service (REST đồng bộ, X-Internal-Token):
   POST /payments/create { orderId, orderCode, amount, method, returnUrl, bankCode }
   → VNPAY/MOMO: nhận về paymentId + paymentUrl
   → COD: nhận về paymentId, status = PENDING, không có paymentUrl
   → Lỗi (GATEWAY_ERROR...) → gọi REST đồng bộ POST /internal/stock/release để hoàn tác
     bước 4, ROLLBACK bước 3, trả lỗi cho client, KHÔNG publish event
7. Nếu paymentMethod = COD (chỉ COD, không áp dụng VNPAY/MOMO):
   a. Gọi Inventory Service (REST đồng bộ): POST /internal/stock/deduct { orderId }
      → Thành công: completed_steps += "PAYMENT_PROCESSED", "STOCK_DEDUCTED"
        (COD không có bước thanh toán qua cổng nên coi payment "processed" ngay tại đây)
      → Thất bại (hiếm, race condition) → gọi REST đồng bộ POST /internal/stock/release,
        ROLLBACK bước 3, trả lỗi cho client, KHÔNG publish event
   b. UPDATE order status = CONFIRMED, saga_status = COMPLETED
      (COD xong hẳn saga ngay trong request này — không cần chờ event nào nữa)
   Nếu paymentMethod = VNPAY/MOMO: order giữ nguyên PENDING, chờ payment.processed (async)
8. Publish Kafka event order.created (kèm paymentId)
   Nếu COD (đã CONFIRMED ở bước 7): publish thêm order.confirmed ngay trong cùng request này
   (Shipping/Notification xử lý như bình thường, chỉ là đến sớm hơn thay vì chờ webhook thanh toán)
9. Trả response cho client (paymentUrl nếu online payment, hoặc message COD)
```

**Lưu ý quan trọng:** Cả bước reserve stock (4) và tạo payment (6) đều **bắt buộc là REST đồng bộ** — phải biết ngay trong cùng 1 request liệu còn hàng hay không, và với online payment còn phải lấy `paymentUrl` để trả về client ngay; không thể chờ qua Kafka (Kafka là async, không kịp trả trong cùng response). Với COD, vì không có cổng thanh toán nào cần chờ, **toàn bộ saga hoàn tất ngay trong request tạo đơn** (bước 7) — khác với online phải chờ `payment.processed` đến sau.

> **Vì sao reserve cũng là REST chứ không phải Kafka:** Inventory Service đã có sẵn `POST /internal/stock/reserve` dùng pessimistic lock, trả lời ngay 200 (còn hàng) hoặc 409 (hết hàng) — same pattern với `POST /payments/create`. Không có lý do phải publish `order.created` rồi chờ Inventory Service tự nghe lại để reserve async, vừa phức tạp hơn vừa không tận dụng được response đồng bộ đã có sẵn.

---

### GET /orders
Lấy danh sách đơn hàng của user, có phân trang + filter theo status.

**Header:** `Authorization: Bearer {accessToken}`

**Query Params**
```
page      = 0
size      = 10
status    = CONFIRMED    (filter, optional)
startDate = 2024-01-01
endDate   = 2024-01-31
```

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách đơn hàng thành công",
  "data": {
    "content": [
      {
        "orderId":     "order-uuid-1",
        "orderCode":   "SN240115001",
        "status":      "CONFIRMED",
        "totalAmount": 448200,
        "itemCount":   2,
        "thumbnail":   "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "firstItemName":"Áo Polo Nam Basic",
        "paymentMethod":"VNPAY",
        "createdAt":   "2024-01-15T10:30:00Z"
      }
    ],
    "page":          0,
    "totalElements": 8,
    "totalPages":    1
  }
}
```

---

### GET /orders/{orderId}
Lấy chi tiết 1 đơn hàng đầy đủ.

**Header:** `Authorization: Bearer {accessToken}`

**Response 200**
```json
{
  "success": true,
  "message": "Lấy chi tiết đơn hàng thành công",
  "data": {
    "orderId":     "order-uuid-1",
    "orderCode":   "SN240115001",
    "status":      "CONFIRMED",
    "items": [
      {
        "variantId":  "var-uuid-1",
        "productName":"Áo Polo Nam Basic",
        "sku":        "POLO-WHITE-S",
        "color":      "Trắng",
        "size":       "S",
        "thumbnail":  "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "unitPrice":  249000,
        "qty":        2,
        "subtotal":   498000
      }
    ],
    "shippingAddress": {
      "fullName":    "Nguyen Van A",
      "phone":       "0901234567",
      "province":    "TP. Hồ Chí Minh",
      "district":    "Quận 1",
      "ward":        "Phường Bến Nghé",
      "streetDetail":"123 Đường Lê Lợi"
    },
    "pricing": {
      "subtotal":    498000,
      "discount":    49800,
      "shippingFee": 0,
      "total":       448200
    },
    "coupon":        "SALE10",
    "paymentMethod": "VNPAY",
    "paymentStatus": "PAID",
    "note":          "Giao giờ hành chính",
    "timeline": [
      { "status": "PENDING",   "at": "2024-01-15T10:30:00Z" },
      { "status": "CONFIRMED", "at": "2024-01-15T10:31:00Z" }
    ],
    "createdAt":     "2024-01-15T10:30:00Z"
  }
}
```

---

### POST /orders/{orderId}/cancel
User hủy đơn hàng (chỉ hủy được khi status = PENDING hoặc CONFIRMED, chưa giao).

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "reason": "Đổi ý không mua nữa"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã hủy đơn hàng. Tiền sẽ được hoàn trong 3-5 ngày.",
  "data": {
    "orderId": "order-uuid-1",
    "status":  "CANCELLED"
  }
}
```

**Response 409** — không hủy được
```json
{
  "success": false,
  "code":    "ORDER_CANNOT_CANCEL",
  "message": "Đơn hàng đang được giao, không thể hủy."
}
```

**Flow:** Đọc `saga_state.completed_steps` để biết cần compensate gì:
```
1. Nếu completed_steps có PAYMENT_PROCESSED:
   a. UPDATE order status = REFUNDING
   b. Gọi REST đồng bộ (X-Internal-Token): POST /payments/{paymentId}/refund
      { orderId, amount, reason: "ORDER_CANCELLED_BY_USER" }
   c. Chờ Payment Service publish payment.refunded → UPDATE order status = REFUNDED
2. Nếu chưa thanh toán (chưa có PAYMENT_PROCESSED):
   → UPDATE order status = CANCELLED ngay
3. Xử lý kho theo đúng bước saga đã tới:
   a. Nếu completed_steps có STOCK_DEDUCTED (đơn đã CONFIRMED, kho đã trừ thật):
      → Gọi REST đồng bộ POST /internal/stock/return { orderId } (Inventory Service)
   b. Ngược lại, nếu completed_steps có STOCK_RESERVED (chưa deduct, chỉ mới giữ chỗ):
      → Gọi REST đồng bộ POST /internal/stock/release { orderId, reason: "ORDER_CANCELLED" }
        (Inventory Service)
   (2 bước loại trừ nhau — 1 reservation chỉ có thể đang RESERVED hoặc đã DEDUCTED, không cả 2)
4. Publish order.cancelled (needReleaseStock = false — đã tự xử lý kho qua REST ở bước 3 rồi)
   → Notification gửi email (Inventory Service không cần consume event này nữa)
```

---

## 2. THANH TOÁN — Payment Service sở hữu webhook

---

Order Service **không** expose endpoint webhook thanh toán (VNPay/MoMo/Stripe). Toàn bộ việc verify chữ ký, chống xử lý trùng (idempotency) và xác nhận thanh toán do **Payment Service** đảm nhiệm (xem `paymentServiceApiSpec.md`) — đây là service duy nhất sở hữu bảng `payments`/`processed_webhooks` nên phải là nguồn xác nhận duy nhất, tránh 2 service cùng verify signature và cùng publish trùng event.

Order Service chỉ phản ứng lại bằng cách consume các Kafka event do Payment Service publish:
- `payment.processed` (SUCCESS / FAILED)
- `payment.refunded`

Xem chi tiết ở PHẦN 3 — mục "Choreography — Order Service consume những event nào".

---

## 3. INTERNAL

---

### GET /internal/orders/{orderId}
Các service khác (Shipping, Notification) gọi để lấy thông tin order.

**Header:** `X-Internal-Token: {sharedSecret}`

**Response 200:** Giống GET /orders/{orderId} nhưng đầy đủ thông tin nội bộ.

---

## 4. ADMIN

---

### GET /admin/orders
Quản lý tất cả đơn hàng, filter mạnh.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Query Params**
```
page         = 0
size         = 20
status       = PENDING
paymentStatus= PAID
keyword      = "SN240115"   (tìm theo orderCode)
userId       = user-uuid-1
startDate    = 2024-01-01
endDate      = 2024-01-31
```

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách đơn hàng thành công",
  "data": {
    "content": [
      {
        "orderId":       "order-uuid-1",
        "orderCode":     "SN240115001",
        "userId":        "user-uuid-1",
        "status":        "CONFIRMED",
        "paymentStatus": "PAID",
        "totalAmount":   448200,
        "itemCount":     2,
        "thumbnail":     "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "firstItemName": "Áo Polo Nam Basic",
        "paymentMethod": "VNPAY",
        "createdAt":     "2024-01-15T10:30:00Z"
      }
    ],
    "page":          0,
    "totalElements": 8,
    "totalPages":    1
  }
}
```

So với `GET /orders` (dành cho user), item ở đây có thêm 2 field: `userId` (biết đơn của khách nào — user tự xem đơn mình thì không cần) và `paymentStatus` (để filter `paymentStatus` ở query param thấy ngay trên list, không cần bấm vào từng đơn).

**Khác biệt về scope so với `GET /orders`:**
- Không giới hạn theo `userId` của người gọi (admin xem được đơn của mọi khách hàng).
- `userId` trong query param là filter **tùy chọn** — để trống thì trả tất cả, điền vào thì lọc riêng 1 khách.

---

### PATCH /admin/orders/{orderId}/status
Admin cập nhật trạng thái đơn (xác nhận, đóng gói...).

**Request Body**
```json
{
  "status": "PROCESSING",
  "note":   "Đang đóng gói"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã cập nhật trạng thái đơn hàng."
}
```

---

## 5. ORDER STATUS — State Machine

```
PENDING ──────► CONFIRMED ──────► PROCESSING ──────► SHIPPING ──────► DELIVERED ──────► COMPLETED
   │                │                                                      
   │                │                                                      
   ▼                ▼                                                      
CANCELLED       CANCELLED                                                 

Trạng thái:
PENDING     — Vừa tạo, chờ thanh toán + reserve stock
CONFIRMED   — Đã thanh toán (hoặc COD) + đã trừ kho THẬT (gọi REST deduct thành công), chờ shop xử lý
PROCESSING  — Shop đang đóng gói
SHIPPING    — Đang giao (Shipping Service cập nhật)
DELIVERED   — Đã giao tới nơi
COMPLETED   — User xác nhận đã nhận (hoặc auto sau 7 ngày)
CANCELLED   — Đã hủy (compensating đã chạy xong)
REFUNDING   — Đang hoàn tiền
REFUNDED    — Đã hoàn tiền xong
```

**Quy tắc chuyển trạng thái:** Chỉ cho phép chuyển theo đúng chiều mũi tên. Mọi chuyển trạng thái sai phải bị từ chối (validate trong Service).

**COD & thu tiền khi giao hàng:** Khi order chuyển sang `DELIVERED` với `paymentMethod = COD`, Order Service gọi REST nội bộ `PATCH /internal/payments/{paymentId}/confirm-cod` bên Payment Service để đánh dấu đã thu tiền (chỉ phục vụ đối soát/lịch sử, không ảnh hưởng đến saga vì đơn COD đã `CONFIRMED` từ trước khi giao).

---

## 6. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `CHECKOUT_TOKEN_EXPIRED` | 409 | Token checkout hết hạn |
| `ORDER_NOT_FOUND` | 404 | Order không tồn tại |
| `ORDER_CANNOT_CANCEL` | 409 | Đơn không ở trạng thái hủy được |
| `INVALID_STATUS_TRANSITION` | 400 | Chuyển trạng thái sai |
| `ORDER_ACCESS_DENIED` | 403 | Order không thuộc về user này |

---

## 7. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| POST | /orders | ✅ | USER |
| GET | /orders | ✅ | USER |
| GET | /orders/{orderId} | ✅ | USER |
| POST | /orders/{orderId}/cancel | ✅ | USER |
| GET | /internal/orders/{orderId} | 🔒 Internal | — |
| GET | /admin/orders | ✅ | ADMIN |
| PATCH | /admin/orders/{orderId}/status | ✅ | ADMIN |

---

---

# PHẦN 2 — DATABASE SCHEMA

---

## Bảng: orders

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| order_code | VARCHAR(20) | NOT NULL, UNIQUE | Mã đơn dễ đọc: SN240115001 |
| user_id | UUID | NOT NULL | Reference sang User Service |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | State machine |
| subtotal | BIGINT | NOT NULL | Tổng tiền hàng |
| discount_amount | BIGINT | NOT NULL, DEFAULT 0 | |
| shipping_fee | BIGINT | NOT NULL, DEFAULT 0 | |
| total_amount | BIGINT | NOT NULL | subtotal - discount + shipping |
| coupon_code | VARCHAR(50) | NULLABLE | |
| payment_method | VARCHAR(20) | NOT NULL | VNPAY / MOMO / COD |
| payment_status | VARCHAR(20) | NOT NULL, DEFAULT 'UNPAID' | UNPAID / PAID / FAILED / REFUNDING / REFUNDED — map từ `payments.status` bên Payment Service (SUCCESS→PAID) |
| transaction_id | VARCHAR(100) | NULLABLE | Mã giao dịch từ cổng thanh toán |
| note | TEXT | NULLABLE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_orders_order_code ON orders(order_code);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
```

---

## Bảng: order_items

Snapshot thông tin sản phẩm lúc đặt — KHÔNG dùng FK sang Product Service. Lưu giá lúc đặt để sau này giá sản phẩm thay đổi không ảnh hưởng đơn cũ.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| order_id | UUID | NOT NULL, FK → orders(id) ON DELETE CASCADE | |
| variant_id | UUID | NOT NULL | Reference logic sang Product Service |
| product_id | UUID | NOT NULL | |
| product_name | VARCHAR(255) | NOT NULL | Snapshot tên |
| sku | VARCHAR(100) | NOT NULL | Snapshot SKU |
| color | VARCHAR(50) | NULLABLE | Snapshot |
| size | VARCHAR(20) | NULLABLE | Snapshot |
| thumbnail | TEXT | NULLABLE | Snapshot ảnh |
| unit_price | BIGINT | NOT NULL | Giá lúc đặt (snapshot) |
| qty | INT | NOT NULL | |
| subtotal | BIGINT | NOT NULL | unit_price × qty |

**Index:**
```sql
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_variant_id ON order_items(variant_id);
```

---

## Bảng: order_shipping_address

Snapshot địa chỉ giao hàng lúc đặt (user có thể đổi địa chỉ sau, đơn cũ vẫn giữ đúng).

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| order_id | UUID | NOT NULL, FK → orders(id) ON DELETE CASCADE | |
| full_name | VARCHAR(100) | NOT NULL | |
| phone | VARCHAR(15) | NOT NULL | |
| province | VARCHAR(100) | NOT NULL | |
| district | VARCHAR(100) | NOT NULL | |
| ward | VARCHAR(100) | NOT NULL | |
| street_detail | VARCHAR(255) | NOT NULL | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_order_shipping_order_id ON order_shipping_address(order_id);
```

---

## Bảng: order_status_history

Lưu lịch sử chuyển trạng thái để build timeline + audit.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| order_id | UUID | NOT NULL, FK → orders(id) ON DELETE CASCADE | |
| from_status | VARCHAR(20) | NULLABLE | NULL nếu là trạng thái đầu |
| to_status | VARCHAR(20) | NOT NULL | |
| note | TEXT | NULLABLE | |
| changed_by | VARCHAR(20) | NOT NULL | SYSTEM / USER / ADMIN |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);
```

---

## Bảng: saga_state — QUAN TRỌNG NHẤT

Theo dõi trạng thái Saga của mỗi đơn hàng để recover khi service crash.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| order_id | UUID | NOT NULL, UNIQUE, FK → orders(id) | |
| saga_status | VARCHAR(20) | NOT NULL | STARTED / COMPLETED / COMPENSATING / COMPENSATED / FAILED |
| current_step | VARCHAR(30) | NOT NULL | Bước hiện tại của Saga |
| completed_steps | JSONB | NOT NULL, DEFAULT '[]' | Mảng các bước đã hoàn thành (để biết cần compensate gì) |
| failure_reason | TEXT | NULLABLE | Lý do fail nếu có |
| retry_count | INT | NOT NULL, DEFAULT 0 | Số lần retry |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**current_step values:**
```
ORDER_CREATED      → đã tạo order
STOCK_RESERVED     → đã reserve hàng
PAYMENT_PROCESSED  → đã thanh toán
STOCK_DEDUCTED     → đã trừ kho thật
ORDER_CONFIRMED    → hoàn tất
```

**completed_steps ví dụ:**
```json
["ORDER_CREATED", "STOCK_RESERVED"]
```
Nếu payment fail ở đây → đọc completed_steps → biết phải release stock (vì có STOCK_RESERVED) nhưng không cần refund (vì chưa PAYMENT_PROCESSED).

**Index:**
```sql
CREATE UNIQUE INDEX idx_saga_state_order_id ON saga_state(order_id);
CREATE INDEX idx_saga_state_status ON saga_state(saga_status);
```

---

# PHẦN 3 — SAGA & COMPENSATING TRANSACTION CHI TIẾT

---

## Bảng mapping Forward ↔ Compensating

| Bước Forward | Action | Compensating Action | Cách trigger compensating |
|---|---|---|---|
| 1. Create order | INSERT order PENDING | UPDATE status = CANCELLED | — |
| 2. Reserve stock | Gọi REST đồng bộ `POST /internal/stock/reserve` — biết ngay còn/hết hàng | Order Service tự gọi REST đồng bộ `POST /internal/stock/release` — ngay trong request nếu lỗi xảy ra cùng lúc tạo order, hoặc trong listener `payment.processed` FAILED, hoặc trong `cancelledOrder()` khi user hủy đơn | REST đồng bộ, **KHÔNG** qua Kafka (ngoại lệ duy nhất: `RESERVATION_EXPIRED` do chính Inventory Service tự phát hiện qua Scheduler và tự release nội bộ — xem event `stock.released` bên dưới) |
| 3. Charge payment | Trừ tiền qua cổng TT | Refund tiền | Gọi REST đồng bộ `POST /payments/{paymentId}/refund` — **KHÔNG** qua Kafka |
| 4. Deduct stock | Online: Order Service tự gọi REST đồng bộ `POST /internal/stock/deduct` ngay trong listener `payment.processed` (SUCCESS). COD: gọi ngay trong `createOrder()` (vì COD không có `payment.processed`) | User hủy đơn sau khi đã CONFIRMED (Case 3) → gọi REST đồng bộ `POST /internal/stock/return` (**không phải** `/release` — endpoint đó chỉ xử lý bản ghi còn `RESERVED`) | REST đồng bộ do Order Service chủ động gọi ở cả 2 trường hợp — **KHÔNG** qua Kafka |
| 5. Confirm order | Online: status = CONFIRMED ngay sau khi gọi deduct REST thành công trong listener `payment.processed`. COD: status = CONFIRMED ngay trong `createOrder()` sau khi deduct REST thành công | — (không cần undo) | — |

> **Vì sao refund là REST chứ không phải event:** Payment Service là chủ sở hữu duy nhất của trạng thái thanh toán (bảng `payments`, `refunds`). Order Service chủ động gọi refund ngay khi biết cần compensate, thay vì publish `order.cancelled` rồi để Payment Service tự suy luận có cần refund hay không — tránh 2 nguồn quyết định logic refund.

---

## Choreography — Order Service consume những event nào

> **Đã đổi so với bản trước:** `stock.reserved` / `stock.insufficient` / `stock.deducted` / `stock.deduct_failed` không còn là event nữa. **Inventory Service không consume Kafka event nào cả** — nó chỉ có REST (`reserve`/`release`/`deduct`) và tự chủ động publish `stock.released` đúng 1 trường hợp (reservation tự hết hạn). Order Service mới là nơi chủ động gọi REST `deduct`/`release` ngay trong listener của chính mình, thay vì trông chờ Inventory Service tự phản ứng lại `payment.processed`/`order.cancelled`.
>
> **Đơn COD:** payment-service không bao giờ publish `payment.processed` cho COD (tiền chỉ thu thật khi giao hàng, xem `confirm-cod`), nên COD **không đi qua choreography bên dưới** — toàn bộ saga (reserve, tạo payment, deduct, confirm) chạy hết trong 1 request `createOrder()` theo kiểu REST đồng bộ (xem bước 7 ở "Flow bên trong" PHẦN 1). Phần Consume dưới đây chỉ áp dụng cho đơn online (VNPAY/MOMO).

```
Consume: payment.processed (SUCCESS)
  → completed_steps += "PAYMENT_PROCESSED"
  → UPDATE order payment_status = PAID, transaction_id = payload.transactionId
  → Gọi REST đồng bộ POST /internal/stock/deduct { orderId } (Inventory Service)
  → UPDATE order status = CONFIRMED
  → saga_status = COMPLETED
  → publish order.confirmed → Shipping tạo vận đơn, Notification gửi email

Consume: payment.processed (FAILED)
  → saga_status = COMPENSATING
  → Gọi REST đồng bộ POST /internal/stock/release { orderId, reason: "PAYMENT_FAILED" } (Inventory Service)
  → UPDATE order status = CANCELLED (reason PAYMENT_FAILED)
  → publish order.cancelled (needReleaseStock = false — đã tự release qua REST ở bước trên rồi)

Consume: stock.released (reason = RESERVATION_EXPIRED — duy nhất Inventory Service tự phát hiện, xem inventoryServiceApiSpec.md)
  → saga_status = COMPENSATING
  → UPDATE order status = CANCELLED (reason RESERVATION_EXPIRED)
  → publish order.cancelled (needReleaseStock = false — Inventory Service đã tự release rồi)
  → KHÔNG gọi REST release (kho đã được release trước khi event này tới)

Consume: payment.refunded (Payment Service xác nhận hoàn tiền xong)
  → UPDATE order status = REFUNDED, payment_status = REFUNDED
  → saga_status = COMPENSATED
  → publish notification "Đã hoàn tiền"
```

---

## Xử lý các trường hợp fail

### Case 1 — Hết hàng khi reserve
```
BEGIN TRANSACTION → INSERT order/items/shipping → [gọi REST reserve → 409 INSUFFICIENT_STOCK]
  • ROLLBACK toàn bộ transaction — order/items/shipping chưa từng commit, không tồn tại trong DB
  • Trả lỗi 409 OUT_OF_STOCK cho client NGAY trong response của POST /orders
  • Không tạo payment, không publish event, không cần compensating gì thêm
    (xử lý gọn trong 1 request đồng bộ vì reserve giờ là REST, không phải Kafka)
```

### Case 2 — Thanh toán thất bại

**Fail ngay lúc tạo order** (bước gọi `POST /payments/create` trong cùng request thất bại):
```
BEGIN TRANSACTION → reserve OK → [gọi payment-service FAIL]
  • Gọi REST đồng bộ POST /internal/stock/release để hoàn tác reserve vừa làm
  • ROLLBACK transaction local, trả lỗi cho client ngay trong response POST /orders
  • Refund? KHÔNG (charge chưa từng thành công)
```

**Fail sau khi order đã tạo xong** (VNPAY/MOMO báo thanh toán thất bại qua `payment.processed` FAILED, tới sau):
```
ORDER_CREATED → STOCK_RESERVED (REST) → [payment.processed FAILED]
Compensating cần làm:
  • Gọi REST đồng bộ POST /internal/stock/release { orderId, reason: "PAYMENT_FAILED" }
  • Order → CANCELLED (reason PAYMENT_FAILED)
  • Publish order.cancelled (needReleaseStock = false — đã tự release ở bước trên rồi)
  • Refund? KHÔNG (charge đã fail, tiền chưa trừ)
  • Gửi email "Thanh toán thất bại"
```

### Case 3 — User hủy sau khi đã thanh toán
```
ORDER_CREATED → STOCK_RESERVED → PAYMENT_PROCESSED → STOCK_DEDUCTED → CONFIRMED
                                                                        → [user cancel]
Compensating cần làm:
  • Order → REFUNDING (ngay khi nhận request cancel)
  • Gọi REST đồng bộ POST /payments/{paymentId}/refund (vì completed_steps có PAYMENT_PROCESSED)
  • Gọi REST đồng bộ POST /internal/stock/return { orderId } (vì completed_steps có STOCK_DEDUCTED,
    KHÔNG dùng /internal/stock/release ở đây — release chỉ xử lý bản ghi còn RESERVED,
    còn case này đã DEDUCTED nên phải dùng /return, xem inventoryServiceApiSpec.md)
  • Publish order.cancelled (chỉ để Notification gửi email, không có ý nghĩa xử lý kho nữa)
  • Chờ Payment Service publish payment.refunded → Order → REFUNDED
  • Gửi email "Đã hủy đơn, hoàn tiền trong 3-5 ngày"
```

---

## Idempotency — chống xử lý trùng

Kafka có thể deliver 1 event nhiều lần (at-least-once). Phải đảm bảo xử lý 1 lần:

```
Mỗi event có eventId (UUID).
Trước khi xử lý:
  1. Check Redis: processed:event:{eventId} tồn tại chưa?
  2. Nếu có → skip (đã xử lý rồi)
  3. Nếu chưa → xử lý → SET processed:event:{eventId} = "1" TTL 24h
```

Đặc biệt quan trọng với payment webhook — VNPay có thể gọi IPN nhiều lần, không được charge/confirm 2 lần.

---

## Recovery khi service crash

```
Vấn đề: Order Service crash sau khi publish order.created (đơn online)
        nhưng trước khi nhận được payment.processed
        → order kẹt ở PENDING mãi mãi?

Giải pháp: Scheduled Job quét saga_state
  Chạy: mỗi 2 phút
  Logic:
    1. SELECT * FROM saga_state
       WHERE saga_status = 'STARTED'
       AND updated_at < NOW() - INTERVAL '15 minutes'
    2. Với mỗi saga bị treo:
       → Xác định đang kẹt ở step nào (current_step)
       → Trigger compensating từ step đó
       → Hoặc retry nếu retry_count < 3
       → Nếu retry_count >= 3 → mark FAILED, alert admin
```

---

## Kafka Events publish từ Order Service

### order.created
```json
{
  "eventId":   "uuid-v4",
  "eventType": "order.created",
  "timestamp": "2024-01-15T10:30:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":     "order-uuid-1",
    "orderCode":   "SN240115001",
    "userId":      "user-uuid-1",
    "totalAmount": 448200,
    "items": [
      { "variantId": "var-uuid-1", "qty": 2 }
    ],
    "shippingAddress": {
      "fullName":    "Nguyen Van A",
      "phone":       "0901234567",
      "province":    "TP. Hồ Chí Minh",
      "district":    "Quận 1",
      "ward":        "Phường Bến Nghé",
      "streetDetail":"123 Đường Lê Lợi"
    }
  }
}
```
**Kafka key:** `orderId`
**Consumers:** Không có consumer bắt buộc trong saga nữa — reserve stock giờ là REST đồng bộ, xảy ra **trước khi** event này được publish (xem flow `POST /orders` ở PHẦN 1). Event này chỉ mang tính thông báo, để service khác (vd Analytics) dùng nếu cần.

> Payment Service **không** consume event này — payment record được tạo trước đó bằng REST đồng bộ `POST /payments/create` (xem flow `POST /orders` ở PHẦN 1), ngay trước khi publish `order.created`.

---

### order.cancelled
```json
{
  "eventId":   "uuid-v4",
  "eventType": "order.cancelled",
  "timestamp": "2024-01-15T10:35:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":         "order-uuid-1",
    "reason":          "PAYMENT_FAILED",
    "needReleaseStock": true,
    "items": [
      { "variantId": "var-uuid-1", "qty": 2 }
    ]
  }
}
```
**Consumers:** Notification Service (gửi email). Inventory Service **không** consume event này nữa — release (nếu cần) đã được Order Service gọi REST đồng bộ tới Inventory Service **trước khi** publish event này (giống hệt lý do refund là REST, không phải event — xem ghi chú `needRefund` bên dưới). `needReleaseStock` gần như luôn là `false` giờ đây, giữ lại field chỉ để tương thích ngược, có thể cân nhắc bỏ nếu Notification Service không dùng tới.

> Không còn field `needRefund` — refund (nếu cần) đã được Order Service gọi REST đồng bộ tới Payment Service **trước khi** publish event này, không phải do Payment Service consume event để tự quyết định.

---

### order.confirmed
```json
{
  "eventId":   "uuid-v4",
  "eventType": "order.confirmed",
  "timestamp": "2024-01-15T10:31:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":     "order-uuid-1",
    "orderCode":   "SN240115001",
    "userId":      "user-uuid-1",
    "shippingAddress": { "...": "..." },
    "items": [ { "...": "..." } ]
  }
}
```
**Consumers:** Shipping Service (tạo vận đơn), Notification Service (email xác nhận)

---

## Redis Keys — Order Service

| Key pattern | Value | TTL | Mục đích |
|-------------|-------|-----|---------|
| `processed:event:{eventId}` | `"1"` | 24 giờ | Idempotency cho Kafka events |
| `order:code:counter:{date}` | INT | đến hết ngày | Sinh order_code tăng dần theo ngày |

---

## Choreography vs Orchestration — tại sao chọn Choreography

| | Choreography | Orchestration |
|---|---|---|
| Cách hoạt động | Mỗi service tự lắng nghe event và quyết định | 1 service trung tâm điều phối tất cả |
| Ưu điểm | Loose coupling, không có single point | Dễ theo dõi flow, logic tập trung |
| Nhược điểm | Khó debug, flow phân tán | Service trung tâm phức tạp |
| Phù hợp | Flow đơn giản, ít bước | Flow phức tạp nhiều nhánh |

**Project này dùng Choreography** vì flow tương đối thẳng (order → stock → payment → confirm) và để học cách services giao tiếp qua event thuần túy. Nếu sau này flow phức tạp hơn có thể chuyển sang Orchestration với Saga orchestrator riêng.
