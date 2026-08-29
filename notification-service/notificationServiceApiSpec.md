# Notification Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8088/api/v1
```

> Port `8088` — các service khác đang dùng: user `8081`, product `8082`, inventory `8083`, cart `8084`, order `8085`, payment `8086`, shipping `8087`.

## Vai trò
Notification Service là **consumer thuần túy** — nó lắng nghe hầu hết các Kafka event trong hệ thống và gửi thông báo qua nhiều kênh (Email, SMS, Push notification). Đây là service ít expose REST API nhất, chủ yếu hoạt động ngầm.

## Đặc điểm kiến trúc
- **Chủ yếu consume Kafka**, ít REST endpoint
- Dùng **template** cho từng loại thông báo
- Gửi **async** qua queue nội bộ (không block Kafka consumer)
- Retry khi gửi fail
- Lưu lịch sử để user xem lại (notification center)

---
---

# PHẦN 1 — API ENDPOINTS

---

## 1. NOTIFICATION CENTER — Thông báo trong app

---

### GET /notifications
Lấy danh sách thông báo của user (chuông thông báo trong app).

**Header:** `Authorization: Bearer {accessToken}`

**Query Params**
```
page   = 0
size   = 20
isRead = false    (filter chưa đọc, optional)
type   = ORDER    (ORDER | PAYMENT | SHIPMENT | PROMOTION | SYSTEM, optional)
```

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách thông báo thành công",
  "data": {
    "content": [
      {
        "notificationId": "noti-uuid-1",
        "type":           "SHIPMENT",
        "title":          "Đơn hàng đang được giao",
        "body":           "Đơn SN240115001 của bạn đang trên đường giao đến.",
        "imageUrl":       "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "actionUrl":      "/orders/order-uuid-1",
        "isRead":         false,
        "createdAt":      "2024-01-17T09:00:00Z"
      },
      {
        "notificationId": "noti-uuid-2",
        "type":           "ORDER",
        "title":          "Đặt hàng thành công",
        "body":           "Đơn SN240115001 đã được xác nhận.",
        "imageUrl":       null,
        "actionUrl":      "/orders/order-uuid-1",
        "isRead":         true,
        "createdAt":      "2024-01-15T10:31:00Z"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 15,
    "totalPages":    1,
    "unreadCount":   3
  }
}
```

> `content / page / size / totalElements / totalPages` — cùng format phân trang với order-service / payment-service. `unreadCount` là field bổ sung riêng của notification center.

---

### GET /notifications/unread-count
Lấy số thông báo chưa đọc (hiển thị badge trên chuông).

**Response 200**
```json
{
  "success": true,
  "message": "Lấy số thông báo chưa đọc thành công",
  "data": {
    "unreadCount": 3
  }
}
```

---

### PATCH /notifications/{notificationId}/read
Đánh dấu 1 thông báo đã đọc.

**Response 200**
```json
{
  "success": true,
  "message": "Đã đánh dấu đã đọc."
}
```

---

### PATCH /notifications/read-all
Đánh dấu tất cả đã đọc.

**Response 200**
```json
{
  "success": true,
  "message": "Đã đánh dấu tất cả đã đọc."
}
```

---

### DELETE /notifications/{notificationId}
Xóa 1 thông báo.

**Response 200**
```json
{
  "success": true,
  "message": "Đã xóa thông báo."
}
```

---

## 2. DEVICE TOKEN — Đăng ký thiết bị nhận push

---

### POST /notifications/devices
Client đăng ký device token (FCM token) để nhận push notification.

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "deviceToken": "fcm-token-xxx...",
  "platform":    "ANDROID",
  "deviceName":  "Samsung Galaxy S23"
}
```

**platform values:** `ANDROID` / `IOS` / `WEB`

**Response 200**
```json
{
  "success": true,
  "message": "Đã đăng ký thiết bị nhận thông báo."
}
```

---

### DELETE /notifications/devices/{deviceToken}
Hủy đăng ký thiết bị (khi logout).

**Response 200**
```json
{
  "success": true,
  "message": "Đã hủy đăng ký thiết bị."
}
```

---

## 3. PREFERENCES — Cài đặt nhận thông báo

---

### GET /notifications/preferences
Lấy cài đặt nhận thông báo của user.

**Response 200**
```json
{
  "success": true,
  "message": "Lấy cài đặt nhận thông báo thành công",
  "data": {
    "email": {
      "orderUpdates":  true,
      "promotions":    true,
      "paymentReceipt":true
    },
    "sms": {
      "orderUpdates":  false,
      "deliveryAlert": true
    },
    "push": {
      "orderUpdates":  true,
      "promotions":    true,
      "flashSale":     true
    }
  }
}
```

---

### PUT /notifications/preferences
Cập nhật cài đặt nhận thông báo.

**Request Body:** Giống response trên, các field optional.

**Response 200**
```json
{
  "success": true,
  "message": "Đã cập nhật cài đặt thông báo."
}
```

**Lưu ý:** Trước khi gửi bất kỳ notification nào, phải check preference của user. Nếu user tắt promotions → không gửi email/push khuyến mãi.

---

## 4. ADMIN — Gửi thông báo thủ công

---

### POST /admin/notifications/broadcast
Gửi thông báo hàng loạt (marketing, thông báo hệ thống).

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Request Body**
```json
{
  "channel":    "PUSH",
  "target":     "ALL",
  "title":      "Flash Sale 12.12 sắp bắt đầu!",
  "body":       "Giảm giá đến 50% toàn bộ sản phẩm. Nhanh tay!",
  "imageUrl":   "https://storage.shopnow.com/banners/flash-sale.jpg",
  "actionUrl":  "/flash-sale",
  "scheduleAt": "2024-12-12T00:00:00Z"
}
```

**target values:** `ALL` / `SEGMENT` (theo nhóm user)
**scheduleAt:** optional, nếu có thì hẹn giờ gửi

**Response 200**
```json
{
  "success": true,
  "data": {
    "broadcastId":   "bc-uuid-1",
    "estimatedReach":15000,
    "status":        "SCHEDULED"
  }
}
```

---

### GET /admin/notifications/history
Lịch sử gửi notification, thống kê tỉ lệ gửi thành công.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Query Params**
```
page      = 0
size      = 20
channel   = EMAIL
status    = SENT
startDate = 2024-01-01
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "notificationId": "noti-uuid-1",
        "channel":        "EMAIL",
        "type":           "ORDER",
        "recipient":      "user@example.com",
        "status":         "SENT",
        "sentAt":         "2024-01-15T10:31:00Z"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 1262,
    "totalPages":    64,
    "stats": {
      "totalSent":   1250,
      "totalFailed": 12,
      "successRate": 99.05
    }
  }
}
```

> `content / page / size / totalElements / totalPages` — cùng format phân trang với `GET /admin/payments` / `GET /admin/shipments`. `stats` là block bổ sung để hiển thị tỉ lệ gửi thành công.

---

## 5. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `NOTIFICATION_NOT_FOUND` | 404 | Không tìm thấy thông báo |
| `DEVICE_TOKEN_INVALID` | 400 | FCM token không hợp lệ |
| `PROVIDER_ERROR` | 502 | Lỗi từ nhà cung cấp email/SMS/push |
| `TEMPLATE_NOT_FOUND` | 404 | Không tìm thấy template |

**Error response format** (giống các service khác):
```json
{
  "success": false,
  "code":    "NOTIFICATION_NOT_FOUND",
  "message": "Không tìm thấy thông báo."
}
```

---

## 6. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| GET | /notifications | ✅ | USER |
| GET | /notifications/unread-count | ✅ | USER |
| PATCH | /notifications/{id}/read | ✅ | USER |
| PATCH | /notifications/read-all | ✅ | USER |
| DELETE | /notifications/{id} | ✅ | USER |
| POST | /notifications/devices | ✅ | USER |
| DELETE | /notifications/devices/{token} | ✅ | USER |
| GET | /notifications/preferences | ✅ | USER |
| PUT | /notifications/preferences | ✅ | USER |
| POST | /admin/notifications/broadcast | ✅ | ADMIN |
| GET | /admin/notifications/history | ✅ | ADMIN |

---
---

# PHẦN 2 — DATABASE SCHEMA

---

## Bảng: notifications

Lưu tất cả thông báo đã gửi (cho notification center + audit).

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| user_id | UUID | NOT NULL | Người nhận |
| channel | VARCHAR(20) | NOT NULL | EMAIL / SMS / PUSH / IN_APP |
| type | VARCHAR(20) | NOT NULL | ORDER / PAYMENT / SHIPMENT / PROMOTION / SYSTEM |
| title | VARCHAR(255) | NOT NULL | |
| body | TEXT | NOT NULL | |
| image_url | TEXT | NULLABLE | |
| action_url | VARCHAR(500) | NULLABLE | Link khi bấm vào notification |
| recipient | VARCHAR(255) | NULLABLE | Email/phone thực tế đã gửi |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING / SENT / FAILED / READ |
| is_read | BOOLEAN | NOT NULL, DEFAULT FALSE | Chỉ áp dụng cho IN_APP |
| retry_count | INT | NOT NULL, DEFAULT 0 | |
| ref_event_id | UUID | NULLABLE | eventId của Kafka event nguồn |
| sent_at | TIMESTAMP | NULLABLE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_id_is_read ON notifications(user_id, is_read)
    WHERE channel = 'IN_APP';
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
```

---

## Bảng: device_tokens

Lưu FCM token của thiết bị để gửi push notification.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| user_id | UUID | NOT NULL | |
| device_token | TEXT | NOT NULL, UNIQUE | FCM token |
| platform | VARCHAR(20) | NOT NULL | ANDROID / IOS / WEB |
| device_name | VARCHAR(100) | NULLABLE | |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| last_used_at | TIMESTAMP | NULLABLE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_device_tokens_token ON device_tokens(device_token);
CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);
```

**Lưu ý:** 1 user có thể có nhiều device token (điện thoại + máy tính + tablet). Gửi push đến tất cả device active của user. Khi FCM báo token invalid → set is_active = false.

---

## Bảng: notification_preferences

Cài đặt nhận thông báo của từng user.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| user_id | UUID | NOT NULL, UNIQUE | |
| email_order_updates | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| email_promotions | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| email_payment_receipt | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| sms_order_updates | BOOLEAN | NOT NULL, DEFAULT FALSE | |
| sms_delivery_alert | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| push_order_updates | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| push_promotions | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| push_flash_sale | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_notification_preferences_user_id ON notification_preferences(user_id);
```

---

## Bảng: notification_templates

Template cho từng loại thông báo (dễ sửa nội dung không cần deploy lại code).

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| code | VARCHAR(50) | NOT NULL, UNIQUE | VD: ORDER_CONFIRMED_EMAIL |
| channel | VARCHAR(20) | NOT NULL | EMAIL / SMS / PUSH |
| subject | VARCHAR(255) | NULLABLE | Tiêu đề (email) |
| body_template | TEXT | NOT NULL | Nội dung có placeholder {{orderCode}} |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Ví dụ template:**
```
code:    ORDER_CONFIRMED_EMAIL
channel: EMAIL
subject: Đơn hàng {{orderCode}} đã được xác nhận
body:    Xin chào {{userName}}, đơn hàng {{orderCode}} trị giá {{totalAmount}}đ
         đã được xác nhận và đang được chuẩn bị. Cảm ơn bạn đã mua sắm!
```

**Index:**
```sql
CREATE UNIQUE INDEX idx_notification_templates_code ON notification_templates(code);
```

---

## Redis Keys — Notification Service

| Key pattern | Value | TTL | Mục đích |
|-------------|-------|-----|---------|
| `processed:event:{eventId}` | `"1"` | 24 giờ | Idempotency Kafka consumer (giống order-service / shipping-service / payment-service) |
| `noti:unread:{userId}` | INT | Không TTL | Cache số thông báo chưa đọc |
| `noti:ratelimit:{userId}:{type}` | INT | 1 giờ | Chống spam (giới hạn số noti/giờ) |

---
---

# PHẦN 3 — KAFKA CONSUMERS (phần chính)

---

## Kafka Event Envelope

Mọi event nhận về là JSON của `KafkaEvent<T>` — **deserialize bằng `ObjectMapper`** (consumer nhận `String`, rồi `objectMapper.readValue(message, new TypeReference<KafkaEvent<T>>(){})`), **giống hệt order-service / shipping-service**:

```json
{
  "eventId":   "uuid-v4",
  "eventType": "order.confirmed",
  "timestamp": "2024-01-15T10:31:00Z",
  "version":   "1.0",
  "payload":   { ... }
}
```

---

## Bảng mapping Event → Notification

| Kafka Event | Publisher | Kênh gửi | Nội dung |
|---|---|---|---|
| `user.registered` | User Service | Email | Email chào mừng + xác thực tài khoản |
| `user.password_reset_requested` | User Service | Email | Link đặt lại mật khẩu |
| `order.created` | Order Service | Push | "Đơn hàng đang được xử lý" |
| `order.confirmed` | Order Service | Email + Push | Email xác nhận đơn + biên lai |
| `order.cancelled` | Order Service | Email + Push | "Đơn hàng đã bị hủy" |
| `payment.processed` (SUCCESS) | Payment Service | Email + SMS | Biên lai thanh toán |
| `payment.processed` (FAILED) | Payment Service | Push | "Thanh toán thất bại, thử lại" |
| `payment.refunded` | Payment Service | Email | "Đã hoàn tiền" |
| `shipment.updated` (IN_TRANSIT) | Shipping Service | Push | "Đơn hàng đang được giao" |
| `shipment.updated` (DELIVERED) | Shipping Service | Push + SMS | "Đơn hàng đã giao thành công" |
| `stock.low_warning` | Inventory Service | Email (admin) | Cảnh báo sắp hết hàng |
| `promotion.flash_sale_starting` | Promotion Service *(Phase 4 — chưa build)* | Push (broadcast) | "Flash sale sắp bắt đầu!" |

> `promotion.flash_sale_starting` chưa hoạt động cho tới khi Promotion Service (Phase 4) tồn tại và publish topic này. Consumer có thể để sẵn nhưng inactive — không nằm trong 8 topic gốc ở `Project_context.md`.

---

## Payload từng event & cách lấy người nhận

| Topic | Payload fields (theo spec của publisher) | Lấy `userId` / người nhận thế nào |
|---|---|---|
| `user.registered` | userId, email, fullName, provider, createdAt | Có sẵn `userId` + `email` + `fullName` trong payload → **không cần gọi REST** |
| `user.password_reset_requested` | userId, email, resetToken, expiresAt | Có sẵn `userId` + `email` trong payload |
| `order.created` | orderId, orderCode, userId, totalAmount, items[], shippingAddress | Có sẵn `userId` |
| `order.confirmed` | orderId, orderCode, userId, shippingAddress, items[] | Có sẵn `userId` |
| `order.cancelled` | orderId, reason, needReleaseStock, items[] | **Không có `userId`** → gọi Order Service (xem dưới) |
| `payment.processed` | orderId, paymentId, status, method, amount, transactionId, paidAt | **Không có `userId`** → gọi Order Service |
| `payment.refunded` | orderId, refundId, amount, refundedAt | **Không có `userId`** → gọi Order Service |
| `shipment.updated` | orderId, shipmentId, trackingCode, carrier, status, description, estimatedDate | **Không có `userId`** → gọi Order Service |
| `stock.low_warning` | variantId, sku, currentStock, threshold | Gửi cho **admin** (email cấu hình sẵn), không cần userId người mua |

**Resolve thông tin người nhận:**
```
1. Có userId trong payload
   → GET http://localhost:8081/api/v1/internal/users/{userId}
     Header: X-Internal-Token: {sharedSecret}
     → nhận { userId, fullName, email, phone }

2. Chỉ có orderId (order.cancelled, payment.*, shipment.updated)
   → GET http://localhost:8085/api/v1/internal/orders/{orderId}
     Header: X-Internal-Token: {sharedSecret}
     → lấy userId + thông tin đơn (orderCode, totalAmount, items...) để render template
   → rồi làm bước 1 với userId vừa lấy nếu cần email/phone
```

---

## Consumer flow chung

```
Kafka event đến (String) → objectMapper.readValue → KafkaEvent<T>
  │
  ├─ 1. Idempotency check: Redis processed:event:{eventId} tồn tại?
  │     └─ Có → skip
  │
  ├─ 2. Xác định loại notification cần gửi (theo eventType)
  │
  ├─ 3. Lấy thông tin người nhận (xem bảng "Payload từng event" ở trên)
  │     └─ Gọi User Service GET /internal/users/{userId}  (Header: X-Internal-Token)
  │     └─ Nếu payload không có userId → gọi Order Service GET /internal/orders/{orderId} trước
  │     └─ Hoặc dùng data có sẵn trong event payload (user.*, order.created/confirmed)
  │
  ├─ 4. Check notification_preferences của user → nếu user tắt loại này thì skip
  │
  ├─ 5. Render template với data từ event
  │
  ├─ 6. Đẩy vào internal queue (async, không block consumer)
  │
  ├─ 7. INSERT notification record (status = PENDING)
  │
  └─ 8. SET Redis processed:event:{eventId} = "1" TTL 24h

Worker xử lý queue:
  │
  ├─ Gửi qua provider (Email/SMS/Push)
  ├─ Thành công → UPDATE status = SENT, sent_at = NOW()
  └─ Fail → retry (tối đa 3 lần) → nếu vẫn fail → status = FAILED
```

> Vì Notification chỉ gửi thông báo (không ảnh hưởng data core), nếu 1 event xử lý fail → log + skip, không làm hỏng cả flow. Không throw để Kafka retry vô hạn với lỗi không thể phục hồi (VD user đã bị xóa).

---

## Kafka — Consumer group & partition

```
Notification Service consume RẤT NHIỀU topic.
Cấu hình:
  - 1 consumer group: notification-service   (không có hậu tố -group)
  - Subscribe nhiều topic cùng lúc
  - Tăng số partition + số consumer instance nếu volume cao
  - concurrency = số partition (mỗi thread xử lý 1 partition)

Kafka message key của các event nguồn = entityId liên quan nhất
(userId cho user.*, orderId cho order.*/payment.*/shipment.updated)
→ cùng 1 đơn hàng, các event tới đúng thứ tự trong cùng partition.
```

---
---

# PHẦN 4 — TÍCH HỢP PROVIDER

---

## Email — Options

| Provider | Ưu điểm | Dùng khi |
|---|---|---|
| **SMTP (Gmail)** | Miễn phí, dễ setup | Học tập, demo |
| **SendGrid** | 100 email/ngày free, có template | Production nhỏ |
| **AWS SES** | Rẻ, scale tốt | Production lớn |

**Với project học tập:** Dùng `JavaMailSender` + SMTP Gmail là đủ. Tạo App Password trong Gmail.

```
Cấu hình SMTP Gmail:
  host: smtp.gmail.com
  port: 587
  username: your-email@gmail.com
  password: {app-password}   (KHÔNG phải mật khẩu Gmail thường)
  starttls: enabled
```

---

## SMS — Options

| Provider | Ghi chú |
|---|---|
| **Twilio** | Quốc tế, có free trial |
| **eSMS / SpeedSMS** | Việt Nam, cần đăng ký brandname |
| **Firebase (thay thế)** | Nếu không có SMS, dùng push thay |

**Với project học tập:** SMS tốn phí thật, nên có thể **mock** — chỉ log ra console hoặc lưu DB, không gửi thật. Vẫn giữ đầy đủ code flow để demo được.

---

## Push Notification — Firebase Cloud Messaging (FCM)

```
FCM là lựa chọn chuẩn cho push notification (miễn phí):

Flow:
1. Client (app/web) đăng ký FCM token → POST /notifications/devices
2. Khi cần push → dùng Firebase Admin SDK
3. Gửi message đến device token
4. FCM đẩy notification đến thiết bị

Gửi đến nhiều device của 1 user:
  → Lấy tất cả device_tokens active của user
  → Gửi multicast message

Xử lý token hết hạn:
  → FCM trả về lỗi UNREGISTERED
  → Set device_token.is_active = false
```

---
---

# PHẦN 5 — CÁC PATTERN QUAN TRỌNG

---

## Async processing — không block Kafka consumer

```
Vấn đề: Nếu gửi email trực tiếp trong Kafka consumer
        → gửi email chậm (2-3s) → consumer bị chậm
        → tồn đọng message trong Kafka

Giải pháp:
  Kafka consumer chỉ làm việc nhẹ:
    1. Nhận event
    2. INSERT notification record (PENDING)
    3. Đẩy vào internal async queue (@Async hoặc TaskExecutor)
    4. Commit Kafka offset ngay

  Worker riêng xử lý việc gửi:
    → Đọc từ queue → gọi provider → update status
```

---

## Retry khi gửi fail

```
Email/SMS/Push provider có thể tạm lỗi.
Retry strategy:
  - Lần 1 fail → retry sau 30s
  - Lần 2 fail → retry sau 2 phút
  - Lần 3 fail → retry sau 10 phút
  - Vẫn fail → status = FAILED, không retry nữa

Dùng Spring Retry hoặc scheduled job quét notification PENDING/FAILED.
```

---

## Rate limiting — chống spam user

```
Không gửi quá nhiều notification cùng loại cho 1 user:
  - Tối đa 1 email marketing/ngày
  - Tối đa 5 push notification/giờ

Check Redis noti:ratelimit:{userId}:{type} trước khi gửi.
```
