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
2. Gọi User Service: GET /internal/users/{userId}
   → lấy địa chỉ giao hàng
3. BEGIN TRANSACTION (local)
   a. INSERT order (status = PENDING)
   b. INSERT order_items (snapshot giá, tên, variant lúc đặt)
   c. INSERT saga_state (status = STARTED, step = ORDER_CREATED)
   COMMIT
4. Publish Kafka event order.created
5. Trả response cho client (paymentUrl nếu online payment)
```

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

**Flow:** Trigger compensating — publish `order.cancelled` → Inventory release stock, Payment refund (nếu đã thanh toán).

---

## 2. PAYMENT CALLBACK — Webhook từ cổng thanh toán

---

### GET /orders/payment/vnpay/callback
VNPay redirect user về sau khi thanh toán (return URL).

**Query Params:** VNPay tự gửi (vnp_ResponseCode, vnp_TxnRef, vnp_SecureHash...)

**Response:** Redirect về frontend
```
http://localhost:3000/orders/{orderId}/result?status=success
```

---

### POST /orders/payment/vnpay/ipn
VNPay gọi server-to-server (IPN — Instant Payment Notification). Đây mới là nguồn tin cậy để xác nhận thanh toán, không phải return URL.

**Request:** VNPay gửi params

**Response 200** — bắt buộc trả về đúng format VNPay yêu cầu
```json
{
  "RspCode": "00",
  "Message": "Confirm Success"
}
```

**Flow bên trong:**
```
1. Verify vnp_SecureHash (chống giả mạo)
2. Kiểm tra idempotency: orderId này đã xử lý chưa? (tránh VNPay gọi 2 lần)
3. Nếu vnp_ResponseCode = "00" → thanh toán thành công
   → publish payment.processed (status = SUCCESS)
4. Nếu khác → thanh toán thất bại
   → publish payment.processed (status = FAILED)
   → trigger compensating
```

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

**Response 200:** Page danh sách order.

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
CONFIRMED   — Đã thanh toán + đã trừ kho, chờ shop xử lý
PROCESSING  — Shop đang đóng gói
SHIPPING    — Đang giao (Shipping Service cập nhật)
DELIVERED   — Đã giao tới nơi
COMPLETED   — User xác nhận đã nhận (hoặc auto sau 7 ngày)
CANCELLED   — Đã hủy (compensating đã chạy xong)
REFUNDING   — Đang hoàn tiền
REFUNDED    — Đã hoàn tiền xong
```

**Quy tắc chuyển trạng thái:** Chỉ cho phép chuyển theo đúng chiều mũi tên. Mọi chuyển trạng thái sai phải bị từ chối (validate trong Service).

---

## 6. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `CHECKOUT_TOKEN_EXPIRED` | 409 | Token checkout hết hạn |
| `ORDER_NOT_FOUND` | 404 | Order không tồn tại |
| `ORDER_CANNOT_CANCEL` | 409 | Đơn không ở trạng thái hủy được |
| `INVALID_STATUS_TRANSITION` | 400 | Chuyển trạng thái sai |
| `PAYMENT_VERIFICATION_FAILED` | 400 | Sai chữ ký webhook |
| `ORDER_ACCESS_DENIED` | 403 | Order không thuộc về user này |

---

## 7. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| POST | /orders | ✅ | USER |
| GET | /orders | ✅ | USER |
| GET | /orders/{orderId} | ✅ | USER |
| POST | /orders/{orderId}/cancel | ✅ | USER |
| GET | /orders/payment/vnpay/callback | ❌ | — |
| POST | /orders/payment/vnpay/ipn | ❌ | — |
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
| payment_status | VARCHAR(20) | NOT NULL, DEFAULT 'UNPAID' | UNPAID / PAID / REFUNDED |
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

| Bước Forward | Action | Compensating Action | Event publish |
|---|---|---|---|
| 1. Create order | INSERT order PENDING | UPDATE status = CANCELLED | `order.created` |
| 2. Reserve stock | reservedQty += qty | reservedQty -= qty (release) | `stock.reserved` |
| 3. Charge payment | Trừ tiền qua cổng TT | Refund tiền | `payment.processed` |
| 4. Deduct stock | stockQty -= qty | Cộng lại stockQty (hiếm khi cần) | — |
| 5. Confirm order | status = CONFIRMED | — (không cần undo) | `order.confirmed` |

---

## Choreography — Order Service consume những event nào

```
Consume: stock.reserved
  → completed_steps += "STOCK_RESERVED"
  → Nếu paymentMethod = COD → gọi deduct luôn, confirm order
  → Nếu online → đã có paymentUrl từ trước, chờ payment.processed

Consume: stock.insufficient (hết hàng khi reserve)
  → saga_status = COMPENSATING
  → UPDATE order status = CANCELLED (reason OUT_OF_STOCK)
  → publish order.cancelled (nhưng stock chưa reserve nên không cần release)

Consume: payment.processed (SUCCESS)
  → completed_steps += "PAYMENT_PROCESSED"
  → publish order.paid → Inventory deduct stock thật
  → UPDATE order status = CONFIRMED, payment_status = PAID

Consume: payment.processed (FAILED)
  → saga_status = COMPENSATING
  → publish order.cancelled với reason PAYMENT_FAILED
  → Inventory consume → release stock (vì completed_steps có STOCK_RESERVED)
  → UPDATE order status = CANCELLED

Consume: stock.released (compensating done)
  → completed_steps -= "STOCK_RESERVED"
  → saga_status = COMPENSATED
  → publish notification "Đặt hàng thất bại"
```

---

## Xử lý các trường hợp fail

### Case 1 — Hết hàng khi reserve
```
ORDER_CREATED → [reserve FAIL]
Compensating cần làm:
  • Order → CANCELLED (reason OUT_OF_STOCK)
  • KHÔNG cần release stock (chưa reserve được)
  • Refund? KHÔNG (chưa charge)
  • Gửi email "Sản phẩm hết hàng"
```

### Case 2 — Thanh toán thất bại
```
ORDER_CREATED → STOCK_RESERVED → [payment FAIL]
Compensating cần làm (ngược thứ tự):
  • Release stock (reservedQty -= qty)
  • Order → CANCELLED (reason PAYMENT_FAILED)
  • Refund? KHÔNG (charge đã fail, tiền chưa trừ)
  • Gửi email "Thanh toán thất bại"
```

### Case 3 — User hủy sau khi đã thanh toán
```
ORDER_CREATED → STOCK_RESERVED → PAYMENT_PROCESSED → STOCK_DEDUCTED → CONFIRMED
                                                      → [user cancel]
Compensating cần làm:
  • Refund tiền (vì đã PAYMENT_PROCESSED)
  • Cộng lại stock (vì đã STOCK_DEDUCTED)
  • Order → REFUNDING → REFUNDED
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
Vấn đề: Order Service crash sau khi publish order.created
        nhưng trước khi nhận stock.reserved
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
**Consumers:** Inventory Service (reserve stock), Payment Service (chuẩn bị charge)

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
    "needRefund":       false,
    "items": [
      { "variantId": "var-uuid-1", "qty": 2 }
    ]
  }
}
```
**Consumers:** Inventory Service (release stock), Payment Service (refund nếu needRefund), Notification Service (gửi email)

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
