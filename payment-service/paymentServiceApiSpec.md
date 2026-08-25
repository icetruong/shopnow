# Payment Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8086/api/v1
```

## Vai trò
Payment Service xử lý toàn bộ thanh toán qua nhiều cổng (VNPay, MoMo, Stripe), được Order Service gọi REST đồng bộ để tạo phiên thanh toán, xử lý webhook callback, và đảm bảo idempotency tuyệt đối — vì đây là service liên quan trực tiếp đến tiền, sai một lần là mất tiền thật.

---

# PHẦN 1 — API ENDPOINTS

---

## 1. PAYMENT — Khởi tạo thanh toán

---

### POST /internal/payments/create
Order Service gọi service-to-service khi tạo đơn hàng cần thanh toán online, để tạo phiên thanh toán và lấy URL redirect. Client **không** gọi thẳng endpoint này — client nhận `paymentUrl` gián tiếp qua response của Order Service.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "orderId":     "order-uuid-1",
  "orderCode":   "SN240115001",
  "userId":      "user-uuid-1",
  "amount":      448200,
  "method":      "VNPAY",
  "returnUrl":   "http://localhost:3000/orders/order-uuid-1/result",
  "bankCode":    "NCB"
}
```

**method values:** `VNPAY` / `MOMO` / `STRIPE` / `COD`
**userId:** chủ đơn hàng, lưu vào `payments.user_id` (NOT NULL)
**bankCode:** optional, chỉ dùng cho VNPay nếu muốn chọn sẵn ngân hàng

**Response 200** — cổng online
```json
{
  "paymentId":   "pay-uuid-1",
  "orderId":     "order-uuid-1",
  "method":      "VNPAY",
  "amount":      448200,
  "status":      "PENDING",
  "paymentUrl":  "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=44820000&...",
  "expiresAt":   "2024-01-15T10:45:00Z"
}
```

**Response 200** — COD (không cần cổng thanh toán)
```json
{
  "paymentId": "pay-uuid-1",
  "method":    "COD",
  "status":    "PENDING",
  "message":   "Thanh toán khi nhận hàng."
}
```

**Flow bên trong:**
```
1. Validate orderId tồn tại, amount khớp với order
2. INSERT payment record (status = PENDING)
3. Theo method, build payment URL:
   - VNPAY  → build URL với vnp_SecureHash (HMAC-SHA512)
   - MOMO   → gọi MoMo API tạo transaction, nhận payUrl
   - STRIPE → tạo Checkout Session, nhận session URL
   - COD    → không tạo URL, chờ giao hàng
4. Trả về paymentUrl
```

**Lưu ý về `returnUrl`:** Giá trị `vnp_ReturnUrl` gửi cho VNPay thực chất luôn là endpoint của chính Payment Service (`.../api/v1/payments/vnpay/return`) — vì chỉ backend mới verify được `vnp_SecureHash`. `returnUrl` trong request body ở trên là **URL frontend cuối cùng** (VD: `http://localhost:3000/orders/order-uuid-1/result`), được lưu lại để `GET /payments/vnpay/return` biết redirect user về đâu sau khi xử lý xong.

---

### GET /payments/{paymentId}
Lấy trạng thái thanh toán.

**Response 200**
```json
{
  "success": true,
  "message": "Lấy thông tin thanh toán thành công",
  "data": {
    "paymentId":     "pay-uuid-1",
    "orderId":       "order-uuid-1",
    "method":        "VNPAY",
    "amount":        448200,
    "status":        "SUCCESS",
    "transactionId": "VNP14238901",
    "paidAt":        "2024-01-15T10:35:00Z",
    "createdAt":     "2024-01-15T10:30:00Z"
  }
}
```

**status values:** `PENDING` / `SUCCESS` / `FAILED` / `EXPIRED` / `REFUNDED` / `REFUNDING`

---

## 2. WEBHOOK / CALLBACK — Cực kỳ quan trọng

> Có 2 loại URL từ cổng thanh toán:
> - **Return URL** (redirect): user được đưa về sau khi thanh toán — CHỈ để hiển thị UI, KHÔNG tin để cập nhật trạng thái.
> - **IPN/Webhook** (server-to-server): cổng gọi thẳng backend — ĐÂY mới là nguồn tin cậy để xác nhận thanh toán.

---

### GET /payments/vnpay/return
VNPay redirect user về sau khi thanh toán.

**Query Params:** VNPay gửi (vnp_ResponseCode, vnp_TxnRef, vnp_SecureHash...)

**Xử lý:**
```
1. Verify vnp_SecureHash
2. CHỈ đọc kết quả để hiển thị UI cho user
   → KHÔNG update payment status ở đây
   → Vì user có thể đóng trình duyệt giữa chừng, return URL không đáng tin
3. Redirect về frontend với query ?status=success|failed
```

**Response:** Redirect
```
http://localhost:3000/orders/order-uuid-1/result?status=success
```

---

### POST /payments/vnpay/ipn
VNPay gọi server-to-server để xác nhận thanh toán. ĐÂY là nguồn tin cậy duy nhất.

**Request:** VNPay gửi params trong query string

**Response 200** — bắt buộc đúng format VNPay
```json
{ "RspCode": "00", "Message": "Confirm Success" }
```

**Flow bên trong (quan trọng nhất toàn service):**
```
1. Verify vnp_SecureHash (HMAC-SHA512 với secret key)
   → Sai → trả { "RspCode": "97", "Message": "Invalid signature" }

2. Tìm payment theo vnp_TxnRef (= orderCode hoặc paymentId)
   → Không tìm thấy → { "RspCode": "01", "Message": "Order not found" }

3. IDEMPOTENCY CHECK:
   → payment.status đã là SUCCESS/FAILED rồi?
   → Nếu rồi → trả { "RspCode": "02", "Message": "Order already confirmed" }
   → (VNPay có thể gọi IPN nhiều lần, không xử lý 2 lần)

4. Verify amount khớp: vnp_Amount / 100 == payment.amount?
   → Sai → { "RspCode": "04", "Message": "Invalid amount" }

5. Xử lý theo vnp_ResponseCode:
   → "00" = thành công:
       - UPDATE payment status = SUCCESS, transactionId, paidAt
       - INSERT payment_transaction log
       - publish Kafka payment.processed (status = SUCCESS)
   → khác:
       - UPDATE payment status = FAILED
       - publish payment.processed (status = FAILED)

6. Trả { "RspCode": "00", "Message": "Confirm Success" }
```

---

### POST /payments/momo/ipn
MoMo webhook — tương tự VNPay nhưng format khác.

**Request Body (MoMo gửi JSON):**
```json
{
  "partnerCode":  "MOMO",
  "orderId":      "SN240115001",
  "requestId":    "req-uuid",
  "amount":       448200,
  "resultCode":   0,
  "transId":      2589341562,
  "signature":    "..."
}
```

**Xử lý:** Verify `signature` (HMAC-SHA256), idempotency check, `resultCode = 0` là thành công, publish `payment.processed`.

**Response 200**
```json
{ "resultCode": 0, "message": "success" }
```

---

### POST /payments/stripe/webhook
Stripe webhook — Stripe gửi event object.

**Header:** `Stripe-Signature: t=...,v1=...`

**Request:** Raw body (không parse trước khi verify signature)

**Xử lý:**
```
1. Verify chữ ký bằng stripe.webhooks.constructEvent(rawBody, sig, endpointSecret)
   → Stripe SDK tự verify, sai thì throw
2. Xử lý theo event.type:
   → "checkout.session.completed" → thanh toán thành công
   → "payment_intent.payment_failed" → thất bại
3. Idempotency: dùng event.id của Stripe làm khóa
4. publish payment.processed
```

**Response 200:** `{ "received": true }`

---

## 3. REFUND — Hoàn tiền

---

### POST /internal/payments/{paymentId}/refund
Order Service gọi **đồng bộ (REST)** khi cần hoàn tiền (user hủy đơn đã thanh toán). Compensating action của Saga — Order Service chủ động gọi ngay khi biết cần compensate, **không** thông qua Kafka event `order.cancelled` (Payment Service không consume event đó).

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "orderId":  "order-uuid-1",
  "amount":   448200,
  "reason":   "ORDER_CANCELLED_BY_USER"
}
```

**Response 200**
```json
{
  "refundId":  "refund-uuid-1",
  "paymentId": "pay-uuid-1",
  "amount":    448200,
  "status":    "REFUNDING",
  "message":   "Yêu cầu hoàn tiền đã được gửi. Xử lý trong 3-5 ngày."
}
```

**Flow:**
```
1. Idempotency: đã refund cho orderId này chưa?
2. Gọi API refund của cổng tương ứng:
   - VNPay  → API hoàn tiền
   - MoMo   → API refund
   - Stripe → stripe.refunds.create()
3. UPDATE payment status = REFUNDING
4. INSERT refund record
5. Khi cổng xác nhận refund xong (webhook) → status = REFUNDED
6. publish payment.refunded
```

---

## 4. INTERNAL

---

### GET /internal/payments/order/{orderId}
Order Service gọi để check trạng thái thanh toán của đơn.

**Response 200**
```json
{
  "orderId":     "order-uuid-1",
  "paymentId":   "pay-uuid-1",
  "status":      "SUCCESS",
  "method":      "VNPAY",
  "amount":      448200,
  "paidAt":      "2024-01-15T10:35:00Z"
}
```

---

### PATCH /internal/payments/{paymentId}/confirm-cod
Order Service gọi khi đơn COD chuyển sang `DELIVERED` (đã thu tiền lúc giao hàng). Chỉ phục vụ đối soát/lưu lịch sử — đơn COD đã `CONFIRMED` từ trước khi giao nên **không** publish lại `payment.processed` (tránh Order Service xử lý trùng logic saga đã hoàn tất).

**Header:** `X-Internal-Token: {sharedSecret}`

**Response 200**
```json
{
  "paymentId": "pay-uuid-1",
  "status":    "SUCCESS",
  "paidAt":    "2024-01-20T14:00:00Z"
}
```

**Flow:**
```
1. Validate payment.method == COD && payment.status == PENDING
2. UPDATE payment status = SUCCESS, paid_at = NOW()
3. INSERT payment_transaction (type = CHARGE, gateway = COD, status = SUCCESS)
   (không publish Kafka event)
```

---

## 5. ADMIN

---

### GET /admin/payments
Danh sách tất cả giao dịch, filter theo status/method/thời gian.

**Query Params**
```
page      = 0
size      = 20
status    = SUCCESS
method    = VNPAY
startDate = 2024-01-01
endDate   = 2024-01-31
```

**Response 200:** Page danh sách payment.

---

### GET /admin/payments/reconciliation
Đối soát giao dịch — so sánh giao dịch trong DB với báo cáo từ cổng thanh toán.

**Query Params**
```
date   = 2024-01-15
method = VNPAY
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "date":            "2024-01-15",
    "method":          "VNPAY",
    "totalInDb":       150,
    "totalAmount":     67230000,
    "matched":         148,
    "mismatched":      2,
    "mismatchDetails": [
      {
        "orderCode":   "SN240115042",
        "dbStatus":    "PENDING",
        "gatewayStatus":"SUCCESS",
        "issue":       "IPN chưa nhận được"
      }
    ]
  }
}
```

---

## 6. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `PAYMENT_NOT_FOUND` | 404 | Không tìm thấy payment |
| `INVALID_SIGNATURE` | 400 | Sai chữ ký webhook |
| `AMOUNT_MISMATCH` | 400 | Số tiền không khớp |
| `PAYMENT_ALREADY_PROCESSED` | 409 | Đã xử lý rồi (idempotency) |
| `PAYMENT_EXPIRED` | 400 | Phiên thanh toán hết hạn |
| `REFUND_ALREADY_DONE` | 409 | Đã hoàn tiền rồi |
| `GATEWAY_ERROR` | 502 | Lỗi từ cổng thanh toán |

---

## 7. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| POST | /internal/payments/create | 🔒 Internal | Internal (Order Service) |
| GET | /payments/{paymentId} | ✅ | USER |
| GET | /payments/vnpay/return | ❌ | — |
| POST | /payments/vnpay/ipn | ❌ Webhook | — |
| POST | /payments/momo/ipn | ❌ Webhook | — |
| POST | /payments/stripe/webhook | ❌ Webhook | — |
| POST | /internal/payments/{paymentId}/refund | 🔒 Internal | Internal (Order Service) |
| GET | /internal/payments/order/{orderId} | 🔒 Internal | Internal (Order Service) |
| PATCH | /internal/payments/{paymentId}/confirm-cod | 🔒 Internal | Internal (Order Service) |
| GET | /admin/payments | ✅ | ADMIN |
| GET | /admin/payments/reconciliation | ✅ | ADMIN |

---

---

# PHẦN 2 — DATABASE SCHEMA

---

## Bảng: payments

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| order_id | UUID | NOT NULL, UNIQUE | Reference sang Order Service |
| order_code | VARCHAR(20) | NOT NULL | Snapshot mã đơn |
| user_id | UUID | NOT NULL | |
| method | VARCHAR(20) | NOT NULL | VNPAY / MOMO / STRIPE / COD |
| amount | BIGINT | NOT NULL | Số tiền (VND) |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | State machine |
| transaction_id | VARCHAR(100) | NULLABLE | Mã giao dịch từ cổng |
| gateway_response | JSONB | NULLABLE | Lưu nguyên response cổng để đối soát |
| expires_at | TIMESTAMP | NULLABLE | Phiên thanh toán hết hạn |
| paid_at | TIMESTAMP | NULLABLE | Thời điểm thanh toán thành công |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_method ON payments(method);
CREATE INDEX idx_payments_transaction_id ON payments(transaction_id);
CREATE INDEX idx_payments_created_at ON payments(created_at DESC);
```

**status state machine:**
```
PENDING → SUCCESS → REFUNDING → REFUNDED
PENDING → FAILED
PENDING → EXPIRED
```

---

## Bảng: payment_transactions

Log mọi lần webhook/callback nhận được — không bao giờ update, chỉ INSERT. Dùng để audit + đối soát + debug.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| payment_id | UUID | NOT NULL, FK → payments(id) | |
| type | VARCHAR(20) | NOT NULL | CHARGE / REFUND / IPN / RETURN |
| gateway | VARCHAR(20) | NOT NULL | VNPAY / MOMO / STRIPE / COD |
| gateway_txn_id | VARCHAR(100) | NULLABLE | Mã giao dịch từ cổng |
| amount | BIGINT | NOT NULL | |
| status | VARCHAR(20) | NOT NULL | SUCCESS / FAILED |
| raw_payload | JSONB | NOT NULL | Toàn bộ data cổng gửi (để đối soát) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_payment_txn_payment_id ON payment_transactions(payment_id);
CREATE INDEX idx_payment_txn_gateway_txn_id ON payment_transactions(gateway_txn_id);
CREATE INDEX idx_payment_txn_created_at ON payment_transactions(created_at DESC);
```

---

## Bảng: refunds

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| payment_id | UUID | NOT NULL, FK → payments(id) | |
| order_id | UUID | NOT NULL | |
| amount | BIGINT | NOT NULL | Số tiền hoàn |
| reason | VARCHAR(50) | NOT NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'REFUNDING' | REFUNDING / REFUNDED / FAILED |
| gateway_refund_id | VARCHAR(100) | NULLABLE | Mã refund từ cổng |
| refunded_at | TIMESTAMP | NULLABLE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_refunds_order_id ON refunds(order_id);
CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX idx_refunds_status ON refunds(status);
```

---

## Bảng: processed_webhooks — IDEMPOTENCY

Bảng chống xử lý trùng webhook. Mỗi webhook có 1 unique key, lưu vào đây trước khi xử lý.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| idempotency_key | VARCHAR(200) | NOT NULL, UNIQUE | Khóa chống trùng (xem dưới) |
| gateway | VARCHAR(20) | NOT NULL | |
| payment_id | UUID | NULLABLE | |
| processed_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**idempotency_key được tạo thế nào:**
```
VNPay:  "vnpay:" + vnp_TxnRef + ":" + vnp_TransactionNo
MoMo:   "momo:"  + orderId + ":" + transId
Stripe: "stripe:" + event.id      (Stripe event.id vốn đã unique)
```

**Index:**
```sql
CREATE UNIQUE INDEX idx_processed_webhooks_key ON processed_webhooks(idempotency_key);
```

---

# PHẦN 3 — IDEMPOTENCY CHI TIẾT

Đây là phần quan trọng nhất — sai idempotency = charge/confirm 2 lần = mất tiền hoặc khách khiếu nại.

---

## Tại sao webhook bị gọi nhiều lần?

```
- Cổng thanh toán retry nếu không nhận được response 200 kịp thời
- Network timeout khiến cổng tưởng thất bại → gọi lại
- VNPay/MoMo có chính sách gọi IPN nhiều lần để đảm bảo backend nhận được
- User refresh trang return URL nhiều lần
```

---

## 2 lớp bảo vệ idempotency

### Lớp 1 — DB unique constraint (chắc chắn nhất)

```
Trước khi xử lý webhook:
1. Tạo idempotency_key theo gateway
2. Thử INSERT vào processed_webhooks với key đó
3. Nếu INSERT thành công → chưa xử lý → tiến hành xử lý
4. Nếu INSERT fail (unique violation) → đã xử lý rồi → skip, trả success luôn

→ Dùng DB unique constraint đảm bảo tuyệt đối, kể cả khi 2 request đến CÙNG LÚC
  (race condition), chỉ 1 cái INSERT được, cái kia fail.
```

### Lớp 2 — Kiểm tra payment status

```
Ngay cả khi qua được lớp 1, vẫn check:
- payment.status đã là SUCCESS/FAILED chưa?
- Nếu rồi → không xử lý lại, trả về kết quả cũ
```

---

## Flow xử lý webhook idempotent hoàn chỉnh

```
POST /payments/vnpay/ipn
  │
  ├─ 1. Verify signature (HMAC-SHA512)
  │     └─ Sai → trả 97, dừng
  │
  ├─ 2. Tạo idempotency_key = "vnpay:{txnRef}:{transNo}"
  │
  ├─ 3. BEGIN TRANSACTION
  │     ├─ INSERT processed_webhooks (idempotency_key)
  │     │   └─ Unique violation? → ROLLBACK → trả "đã xử lý" (RspCode 02)
  │     │
  │     ├─ SELECT payment FOR UPDATE (lock row)
  │     │
  │     ├─ Check payment.status != PENDING? → ROLLBACK → trả "đã xử lý"
  │     │
  │     ├─ Verify amount khớp
  │     │
  │     ├─ UPDATE payment status = SUCCESS/FAILED
  │     ├─ INSERT payment_transaction log
  │     └─ COMMIT
  │
  ├─ 4. Publish Kafka payment.processed
  │     (publish SAU COMMIT để tránh publish rồi transaction rollback)
  │
  └─ 5. Trả RspCode 00
```

---

## Payment Service không consume Kafka event từ Order Service

Payment Service **chỉ** expose REST API (`POST /internal/payments/create`, `POST /internal/payments/{paymentId}/refund`, `PATCH /internal/payments/{paymentId}/confirm-cod`) và publish event — không consume `order.created` hay `order.cancelled`. Order Service là bên chủ động gọi REST đồng bộ khi cần tạo payment hoặc refund, để tránh 2 service cùng verify/quyết định trạng thái thanh toán qua 2 con đường khác nhau (REST + Kafka).

---

# PHẦN 4 — SO SÁNH 3 CỔNG THANH TOÁN

| | VNPay | MoMo | Stripe |
|---|---|---|---|
| Phổ biến ở | Việt Nam | Việt Nam | Quốc tế |
| Chữ ký | HMAC-SHA512 | HMAC-SHA256 | HMAC-SHA256 (SDK tự verify) |
| Cách tạo payment | Build URL với hash | Gọi API nhận payUrl | Tạo Checkout Session |
| Webhook | IPN (query params) | IPN (JSON body) | Webhook (event object) |
| Idempotency key | txnRef + transNo | orderId + transId | event.id (có sẵn) |
| Sandbox test | Có, dễ | Có | Có, rất tốt (test cards) |
| Số tiền | Nhân 100 (xu) | Nguyên VND | Nhân 100 (cents) |

**Lưu ý quan trọng về số tiền:**
```
VNPay:  amount × 100  → 448200 gửi thành 44820000
Stripe: amount × 100  → nếu USD thì cents; VND thì Stripe không hỗ trợ decimal
MoMo:   amount nguyên → 448200
```

---

## Kafka Events publish từ Payment Service

### payment.processed
```json
{
  "eventId":   "uuid-v4",
  "eventType": "payment.processed",
  "timestamp": "2024-01-15T10:35:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":       "order-uuid-1",
    "paymentId":     "pay-uuid-1",
    "status":        "SUCCESS",
    "method":        "VNPAY",
    "amount":        448200,
    "transactionId": "VNP14238901",
    "paidAt":        "2024-01-15T10:35:00Z"
  }
}
```
**Kafka key:** `orderId`
**Consumers:** Order Service (update status), Notification Service (email biên lai)

---

### payment.refunded
```json
{
  "eventId":   "uuid-v4",
  "eventType": "payment.refunded",
  "timestamp": "2024-01-15T11:00:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":   "order-uuid-1",
    "refundId":  "refund-uuid-1",
    "amount":    448200,
    "refundedAt":"2024-01-15T11:00:00Z"
  }
}
```
**Kafka key:** `orderId`
**Consumers:** Order Service (update status = REFUNDED), Notification Service

---

## Redis Keys — Payment Service

| Key pattern | Value | TTL | Mục đích |
|-------------|-------|-----|---------|
| `payment:pending:{paymentId}` | orderId | 15 phút | Track phiên thanh toán đang chờ |

---

# PHẦN 5 — SECURITY NOTES

```
1. KHÔNG BAO GIỜ tin return URL để cập nhật trạng thái — chỉ tin IPN/webhook server-to-server
2. LUÔN verify chữ ký trước khi xử lý bất kỳ webhook nào
3. LUÔN verify amount khớp — chống user sửa số tiền trên URL
4. Secret key của cổng thanh toán để trong biến môi trường, KHÔNG commit vào Git
5. Webhook endpoint phải public (không qua JWT auth) nhưng bảo vệ bằng signature verification
6. Log toàn bộ raw payload vào payment_transactions để đối soát khi có tranh chấp
7. Gateway response lưu vào JSONB — khi khách khiếu nại có bằng chứng
```