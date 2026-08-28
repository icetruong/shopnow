# Shipping Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8087/api/v1
```

> Port `8087` — các service khác đang dùng: user `8081`, product `8082`, inventory `8083`, cart `8084`, order `8085`, payment `8086`.

## Vai trò
Shipping Service tích hợp với các đơn vị vận chuyển (GHN — Giao Hàng Nhanh, GHTK — Giao Hàng Tiết Kiệm), tính phí ship, tạo vận đơn, và theo dõi trạng thái giao hàng qua webhook từ nhà vận chuyển.

Service này **không** giữ địa chỉ giao hàng gốc — nó nhận địa chỉ dạng **text** (province/district/ward là tên) từ event `order.confirmed` của Order Service, rồi **tự map** sang mã địa giới riêng của từng nhà vận chuyển (xem PHẦN 3).

---

# PHẦN 1 — API ENDPOINTS

---

## 1. SHIPPING FEE — Tính phí vận chuyển

---

### POST /shipping/calculate-fee
Tính phí ship trước khi checkout. Client gọi để hiển thị phí ship cho user chọn.

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "toDistrictId":  1450,
  "toWardCode":    "21012",
  "weight":        500,
  "length":        20,
  "width":         15,
  "height":        10,
  "insuranceValue": 448200
}
```

**Ghi chú:**
- Điểm gửi (`fromDistrictId` / `fromWardCode`) lấy từ config `shipping.from.*` — client không truyền.
- `weight`: gram (tổng khối lượng đơn hàng)
- `length/width/height`: cm (kích thước gói hàng) — client có thể bỏ qua, service dùng `shipping.default-package.*`
- `insuranceValue`: giá trị đơn hàng (để tính bảo hiểm)
- `toDistrictId/toWardCode`: mã địa giới của GHN. Nếu client chỉ có tên tỉnh/quận/phường thì gọi trước 3 endpoint `/shipping/provinces|districts|wards` để lấy mã.

**Response 200** — trả về phí của nhiều nhà vận chuyển để user chọn
```json
{
  "success": true,
  "message": "Tính phí vận chuyển thành công",
  "data": [
    {
      "carrier":       "GHN",
      "serviceId":     "53320",
      "serviceName":   "Giao hàng nhanh",
      "fee":           30000,
      "estimatedDays": 2,
      "estimatedDate": "2024-01-17"
    },
    {
      "carrier":       "GHN",
      "serviceId":     "53321",
      "serviceName":   "Giao hàng tiêu chuẩn",
      "fee":           22000,
      "estimatedDays": 4,
      "estimatedDate": "2024-01-19"
    },
    {
      "carrier":       "GHTK",
      "serviceId":     "road",
      "serviceName":   "Giao hàng đường bộ",
      "fee":           25000,
      "estimatedDays": 3,
      "estimatedDate": "2024-01-18"
    }
  ]
}
```

> `serviceId` luôn trả về dạng **string** (GHN là số, GHTK là chữ — service chuẩn hóa hết về string; cột DB cũng là VARCHAR).

**Flow bên trong:**
```
1. Gọi song song API tính phí của GHN và GHTK
   - GHN:  POST /v2/shipping-order/fee
   - GHTK: POST /services/shipment/fee
2. Chuẩn hóa response về cùng format
3. Cache kết quả vào Redis (key theo from-to-weight) TTL 1 giờ
4. Trả về danh sách để user chọn
```

---

### GET /shipping/provinces
Lấy danh sách tỉnh/thành từ GHN (dùng để đổ dropdown địa chỉ).

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách tỉnh thành công",
  "data": [
    { "provinceId": 202, "provinceName": "TP. Hồ Chí Minh" },
    { "provinceId": 201, "provinceName": "Hà Nội" }
  ]
}
```

---

### GET /shipping/districts?provinceId={id}
Lấy quận/huyện theo tỉnh.

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách quận huyện thành công",
  "data": [
    { "districtId": 1442, "districtName": "Quận 1" },
    { "districtId": 1443, "districtName": "Quận 2" }
  ]
}
```

---

### GET /shipping/wards?districtId={id}
Lấy phường/xã theo quận.

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách phường xã thành công",
  "data": [
    { "wardCode": "21012", "wardName": "Phường Bến Nghé" },
    { "wardCode": "21013", "wardName": "Phường Bến Thành" }
  ]
}
```

**Lưu ý:** 3 endpoint trên proxy lại data từ GHN, nên cache mạnh (TTL 24h) vì data địa giới hầu như không đổi. Data này cũng chính là nguồn để build bảng `location_mappings` (xem PHẦN 3).

---

## 2. SHIPMENT — Vận đơn

---

### POST /internal/shipments
Tạo vận đơn cho 1 order. Endpoint nội bộ — dùng bởi **retry job** và **admin** (tạo lại vận đơn khi lần trước fail). Luồng bình thường vận đơn được tạo tự động qua Kafka consumer `order.confirmed` (xem mục 5), **không phải** Order Service gọi endpoint này.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{ "orderId": "order-uuid-1" }
```

> Chỉ cần `orderId`. Toàn bộ dữ liệu còn lại Shipping Service tự thu thập (xem "Flow tạo vận đơn" ở mục 5) — giống hệt code path của consumer `order.confirmed`, để không có 2 chỗ dựng shipment khác nhau.

**Response 201**
```json
{
  "success": true,
  "data": {
    "shipmentId":    "ship-uuid-1",
    "orderId":       "order-uuid-1",
    "carrier":       "GHN",
    "trackingCode":  "GHN123456789",
    "status":        "READY_TO_PICK",
    "estimatedDate": "2024-01-17",
    "shippingLabel": "https://storage.shopnow.com/labels/GHN123456789.pdf"
  }
}
```

**Response 200** — vận đơn của order này đã tồn tại (idempotent, không tạo trùng)
```json
{
  "success": true,
  "data": { "shipmentId": "ship-uuid-1", "status": "READY_TO_PICK" }
}
```

**Response 422** — không map được địa chỉ sang mã nhà vận chuyển
```json
{
  "success": false,
  "code":    "ADDRESS_MAPPING_FAILED",
  "message": "Không xác định được mã quận/phường cho địa chỉ giao hàng."
}
```
Khi đó shipment vẫn được INSERT với `status = PENDING` + `failure_reason` để admin xử lý tay / retry job chạy lại.

---

### GET /shipments/order/{orderId}
Lấy thông tin vận đơn theo orderId (client theo dõi đơn).

**Header:** `Authorization: Bearer {accessToken}`

> Chỉ trả về nếu `shipments.user_id` == user trong token (hoặc caller có `ROLE_ADMIN`). Sai chủ → `403 SHIPMENT_ACCESS_DENIED`.

**Response 200**
```json
{
  "success": true,
  "data": {
    "shipmentId":   "ship-uuid-1",
    "orderId":      "order-uuid-1",
    "carrier":      "GHN",
    "trackingCode": "GHN123456789",
    "status":       "IN_TRANSIT",
    "estimatedDate":"2024-01-17",
    "timeline": [
      {
        "status":    "READY_TO_PICK",
        "description":"Đơn hàng đã tạo, chờ lấy hàng",
        "location":  "Kho ShopNow Q1",
        "at":        "2024-01-15T11:00:00Z"
      },
      {
        "status":    "PICKED_UP",
        "description":"Đã lấy hàng",
        "location":  "Kho GHN HCM",
        "at":        "2024-01-15T15:00:00Z"
      },
      {
        "status":    "IN_TRANSIT",
        "description":"Đang vận chuyển",
        "location":  "Trung tâm phân loại GHN",
        "at":        "2024-01-16T08:00:00Z"
      }
    ]
  }
}
```

**Response 404** — order chưa có vận đơn (`SHIPMENT_NOT_FOUND`).

---

### GET /shipments/{trackingCode}/track
Tra cứu hành trình đơn theo **mã vận đơn** — endpoint **public, không cần đăng nhập**. Dùng cho:
- Trang "Tra cứu đơn hàng" cho khách vãng lai (chưa/không login).
- Link theo dõi gửi qua email/SMS ("Theo dõi đơn của bạn").
- Widget tracking nhúng ngoài app.

**Header:** _(không có — public)_

**Path param**

| Param | Kiểu | Ghi chú |
|-------|------|---------|
| `trackingCode` | string | Mã vận đơn nhà vận chuyển in trên bill (VD `GHN123456789`). Khớp chính xác, phân biệt HOA/thường theo đúng mã carrier trả. |

**Response 200**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "trackingCode":  "GHN123456789",
    "carrier":       "GHN",
    "status":        "IN_TRANSIT",
    "estimatedDate": "2024-01-17",
    "timeline": [
      {
        "status":    "READY_TO_PICK",
        "description":"Đơn hàng đã tạo, chờ lấy hàng",
        "location":  "Kho ShopNow Q1",
        "at":        "2024-01-15T11:00:00Z"
      },
      {
        "status":    "PICKED_UP",
        "description":"Đã lấy hàng",
        "location":  "Kho GHN HCM",
        "at":        "2024-01-15T15:00:00Z"
      },
      {
        "status":    "IN_TRANSIT",
        "description":"Đang vận chuyển",
        "location":  "Trung tâm phân loại GHN",
        "at":        "2024-01-16T08:00:00Z"
      }
    ]
  }
}
```

**Khác gì so với `GET /shipments/order/{orderId}`** — cùng một vận đơn, nhưng bản public bị lược bớt và đổi cách định danh / xác thực:

| Tiêu chí | `GET /shipments/order/{orderId}` | `GET /shipments/{trackingCode}/track` |
|----------|----------------------------------|--------------------------------------|
| Định danh | `orderId` — UUID nội bộ ShopNow | `trackingCode` — mã carrier in trên bill |
| Auth | `Authorization: Bearer` **bắt buộc**; check `shipments.user_id == token` hoặc `ROLE_ADMIN` | **Không** — ai có mã đều tra được |
| Đối tượng | User đã login (màn "Đơn của tôi"), admin | Khách vãng lai, link chia sẻ, widget ngoài |
| `data.shipmentId` | ✅ có | ❌ bỏ (UUID nội bộ) |
| `data.orderId` | ✅ có | ❌ bỏ (không lộ mapping order) |
| `data.carrier` / `trackingCode` / `status` / `estimatedDate` | ✅ | ✅ |
| `data.timeline[]` (`status`/`description`/`location`/`at`) | ✅ | ✅ |
| Xem được khi shipment còn `PENDING` (chưa có `trackingCode`) | ✅ (tra bằng `orderId`) | ❌ (chưa có mã để tra) |
| Nguồn dữ liệu | Chỉ DB | DB, + tuỳ chọn gọi carrier realtime (xem Flow) |
| Not found | 404 `SHIPMENT_NOT_FOUND` | 404 `SHIPMENT_NOT_FOUND` (không phân biệt mã sai / không tồn tại / PENDING → chống dò mã) |
| Rate limit | Theo user | Theo **IP** (VD 30 req/phút/IP; vượt → `429`) |

> `ShipmentResponse` hiện **không** chứa tên/SĐT/địa chỉ người nhận, phí ship hay COD, nên phần field còn lại chia sẻ công khai được. Nếu sau này nhét field nhạy cảm vào `ShipmentResponse`, endpoint này **phải** tách DTO riêng (`ShipmentTrackingResponse`) chứ không tái dùng.

**Response 404** — không tìm thấy mã vận đơn (`SHIPMENT_NOT_FOUND`)
```json
{ "success": false, "code": "SHIPMENT_NOT_FOUND", "message": "Không tìm thấy vận đơn với mã này." }
```

**Flow bên trong**
```
1. Tìm shipment theo tracking_code (UNIQUE idx_shipments_tracking_code).
   Không có → 404 SHIPMENT_NOT_FOUND.
2. Đọc timeline: shipment_tracking WHERE shipment_id = ? ORDER BY happened_at ASC.
3. (Tuỳ chọn) REALTIME — chỉ khi status chưa kết thúc (DELIVERED/RETURNED/CANCELLED)
   và bản ghi tracking mới nhất cũ hơn N phút:
   gọi carrier.getTracking(trackingCode)
     - GHN:  POST /v2/shipping-order/detail
     - GHTK: GET  /services/shipment/v2/{trackingCode}
   → INSERT event mới vào shipment_tracking (idempotent theo idempotency_key như webhook),
     UPDATE shipments.status nếu tiến theo state machine.
   Lỗi carrier → bỏ qua, vẫn trả data từ DB (KHÔNG trả 502).
   carrier.mode=mock → bỏ hẳn bước này (getTracking chỉ đọc lại DB).
4. Cache Redis shipping:track:{trackingCode} TTL 60s (giảm tải, chống spam tra cứu).
5. Build data (KHÔNG kèm shipmentId/orderId) → 200.
```

---

### POST /internal/shipments/{shipmentId}/cancel
Hủy vận đơn theo `shipmentId` — đường gọi **tay** (admin UI, hoặc service khác), song song với consumer `order.cancelled`.
Cùng dùng chung phần lõi hủy (`cancelShipmentInternal`) với consumer; khác ở chỗ endpoint **để lỗi nổi lên cho caller** thay vì chỉ log.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body** *(tùy chọn)*
```json
{ "reason": "ADMIN_CANCEL" }
```
> `reason` chỉ để ghi vào timeline (`"Đơn hàng đã bị hủy (ADMIN_CANCEL)"`). Bỏ trống / không gửi body → `"Đơn hàng đã bị hủy"`.

**Response 200** — hủy thành công, **hoặc** shipment đã `CANCELLED` từ trước (idempotent)
```json
{
  "success": true,
  "message": "Đã hủy vận đơn."
}
```
Khi hủy: gọi carrier hủy đơn (nếu đã có `trackingCode`) → `shipments.status = CANCELLED` → INSERT `shipment_tracking` → publish `shipment.updated`.

**Response 404** — không tìm thấy `shipmentId` (`SHIPMENT_NOT_FOUND`).

**Response 409** — không hủy được: shipment đang `PICKED_UP` / `IN_TRANSIT` / `DELIVERED` / `FAILED` / `RETURNED`, hoặc carrier từ chối hủy.
```json
{
  "success": false,
  "code":    "SHIPMENT_CANNOT_CANCEL",
  "message": "Đơn đã được lấy hàng, không thể hủy."
}
```

> **So với consumer `order.cancelled`:** consumer tra shipment theo `orderId`, **nuốt** `SHIPMENT_CANNOT_CANCEL` (chỉ `log.warn` + alert admin) để không làm fail cả event; endpoint này tra theo `shipmentId` và trả thẳng 404 / 409 cho caller. Xem thêm mục 5 (Consumer `order.cancelled`).

---

## 3. WEBHOOK — Cập nhật trạng thái từ nhà vận chuyển

---

### POST /shipping/webhook/ghn
GHN gọi server-to-server khi trạng thái đơn thay đổi.

**Request Body (GHN gửi):**
```json
{
  "orderCode":  "GHN123456789",
  "status":     "delivering",
  "description":"Đang giao hàng",
  "warehouse":  "Kho GHN Q3",
  "time":       "2024-01-17T09:00:00Z"
}
```

**Flow bên trong:**
```
1. Verify token/signature từ GHN (header, so với carrier.ghn.webhook-secret)
2. Tìm shipment theo trackingCode (orderCode của GHN)
3. IDEMPOTENCY: INSERT processed_shipping_webhooks (idempotency_key). Trùng key → 200, bỏ qua.
4. Map status GHN → status nội bộ:
   - "ready_to_pick" → READY_TO_PICK
   - "picking"       → PICKED_UP
   - "delivering"    → IN_TRANSIT
   - "delivered"     → DELIVERED
   - "return"        → RETURNED
   - "cancel"        → CANCELLED
5. UPDATE shipment status (chỉ tiến theo state machine, không lùi)
6. INSERT shipment_tracking (timeline, kèm carrier_status gốc)
7. publish Kafka shipment.updated
```

**Response 200:** `{ "success": true }`

---

### POST /shipping/webhook/ghtk
GHTK webhook — tương tự nhưng format khác.

**Request Body (GHTK):**
```json
{
  "partner_id":   "SN240115001",
  "label_id":     "GHTK987654",
  "status_id":    5,
  "status_text":  "Đang giao hàng",
  "action_time":  "2024-01-17 09:00:00"
}
```

**Map status_id GHTK:**
```
-1 = huỷ           → CANCELLED
1  = chưa tiếp nhận → READY_TO_PICK
2  = đã lấy hàng    → PICKED_UP
3  = đang giao      → IN_TRANSIT
5  = đã giao        → DELIVERED
6  = không lấy được → FAILED
```

---

## 4. ADMIN

---

### GET /admin/shipments
Danh sách toàn bộ vận đơn cho trang quản trị, có filter. Dùng để admin theo dõi đơn đang giao và **lọc nhanh các đơn `PENDING` bị kẹt** (map địa chỉ fail / carrier API lỗi) để bấm retry.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Query Params** — tất cả tùy chọn; để trống thì không lọc theo tiêu chí đó

| Param | Mặc định | Ý nghĩa |
|-------|----------|---------|
| `page` | `0` | Trang (0-based) |
| `size` | `20` | Số item / trang |
| `status` | — | `ShipmentStatus`: `PENDING`, `READY_TO_PICK`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `FAILED`, `RETURNED`, `CANCELLED` |
| `carrier` | — | `GHN` / `GHTK` |
| `failureReason` | — | `ADDRESS_MAPPING_FAILED` / `CARRIER_API_ERROR` — lọc riêng đơn `PENDING` đang kẹt |
| `keyword` | — | Tìm gần đúng theo `trackingCode` hoặc `orderCode` |
| `orderId` | — | Lọc đúng 1 đơn hàng |
| `userId` | — | Lọc đơn của đúng 1 khách |
| `startDate` / `endDate` | — | Khoảng `createdAt`, dạng `YYYY-MM-DD` |

Sắp xếp mặc định: `createdAt` giảm dần (mới nhất trước).

**Response 200**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "content": [
      {
        "shipmentId":    "ship-uuid-1",
        "orderId":       "order-uuid-1",
        "orderCode":     "SN240115001",
        "userId":        "user-uuid-1",
        "carrier":       "GHN",
        "trackingCode":  "GHN123456789",
        "status":        "IN_TRANSIT",
        "toName":        "Nguyen Van A",
        "toProvince":    "TP. Hồ Chí Minh",
        "shippingFee":   30000,
        "codAmount":     0,
        "estimatedDate": "2024-01-17",
        "failureReason": null,
        "createdAt":     "2024-01-15T10:31:00Z"
      }
    ],
    "page":          0,
    "totalElements": 8,
    "totalPages":    1
  }
}
```

**Field của mỗi item**

| Field | Ghi chú |
|-------|---------|
| `shipmentId` | ID vận đơn — dùng cho `reprint-label`, `retry`, `cancel` |
| `orderId` / `orderCode` | Đơn hàng gốc bên Order Service |
| `userId` | Khách đặt đơn |
| `trackingCode` | Mã vận đơn nhà vận chuyển — `null` khi shipment còn `PENDING` (chưa tạo được đơn với carrier) |
| `status` | Trạng thái nội bộ |
| `toName` / `toProvince` | Tóm tắt người nhận — đủ để nhận diện trên list, chi tiết xem `GET /shipments/order/{orderId}` |
| `shippingFee` / `codAmount` | VND |
| `failureReason` | Chỉ khác `null` khi `status = PENDING` — lý do kẹt, quyết định có retry được không |
| `createdAt` | Thời điểm tạo shipment |

> Không giới hạn theo người gọi — admin xem được đơn của mọi khách. `userId` / `orderId` ở query param chỉ là filter tùy chọn.

---

### POST /admin/shipments/{shipmentId}/reprint-label
Xin lại link PDF nhãn vận đơn (shipping label) để in — dùng khi nhãn cũ bị mất/hỏng, hoặc admin cần in lại từ máy khác. **Không** tạo lại vận đơn, chỉ lấy lại nhãn của đơn đã có.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Request:** không có body.

**Flow bên trong**
```
1. Tìm shipment theo shipmentId. Không có → 404 SHIPMENT_NOT_FOUND.
2. Shipment chưa có trackingCode (đang PENDING) → 409 CONFLICT
   ("Vận đơn chưa được tạo với nhà vận chuyển, chưa có nhãn.").
3. Gọi carrier lấy nhãn:
   - GHN:  dựng URL in nhãn kèm token
   - GHTK: tải file PDF về rồi lưu ra storage
   → cập nhật shipments.shipping_label_url.
4. Trả URL nhãn.
```

**Response 200**
```json
{
  "success": true,
  "message": "OK",
  "data": { "shippingLabel": "https://storage.shopnow.com/labels/GHN123456789.pdf" }
}
```

**Response 404** — không tìm thấy `shipmentId` (`SHIPMENT_NOT_FOUND`).

**Response 409** — shipment còn `PENDING`, chưa có nhãn để in (`CONFLICT`).

> `carrier.mode=mock`: `MockCarrierClient.getLabelUrl` trả `https://mock.local/labels/{trackingCode}.pdf`.

---

### POST /admin/shipments/{shipmentId}/retry
Ép tạo lại vận đơn với nhà vận chuyển cho shipment đang `PENDING` (địa chỉ đã được sửa map, hoặc carrier API đã ổn). **Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Response 200:** giống `POST /internal/shipments`.

---

## 5. KAFKA

---

### Consumer: order.confirmed → tạo vận đơn

**Topic:** `order.confirmed` · **group-id:** `shipping-service`

Message nhận về là JSON của `KafkaEvent<OrderConfirmPayload>` (deserialize bằng `ObjectMapper` như các listener của order-service):

```json
{
  "eventId":   "uuid-v4",
  "eventType": "order.confirmed",
  "timestamp": "2024-01-15T10:31:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":   "order-uuid-1",
    "orderCode": "SN240115001",
    "userId":    "user-uuid-1",
    "shippingAddress": {
      "fullName":     "Nguyen Van A",
      "phone":        "0901234567",
      "province":     "TP. Hồ Chí Minh",
      "district":     "Quận 1",
      "ward":         "Phường Bến Nghé",
      "streetDetail": "123 Đường Lê Lợi"
    },
    "items": [
      { "variantId": "var-uuid-1", "qty": 2 }
    ]
  }
}
```

> ⚠️ Payload này **chỉ có địa chỉ dạng text và `variantId/qty`** — không có mã district/ward, không có phí ship, không có `paymentMethod`, không có khối lượng. Những thứ đó lấy ở bước dưới.

**Flow tạo vận đơn (dùng chung cho `POST /internal/shipments`):**
```
0. IDEMPOTENCY: Redis processed:event:{eventId} tồn tại? → skip.
   Đồng thời check shipments theo order_id (UNIQUE) → đã có → skip, coi như thành công.

1. ENRICH: gọi Order Service
   GET http://localhost:8085/api/v1/internal/orders/{orderId}
   Header: X-Internal-Token
   → lấy: pricing.shippingFee, pricing.total, paymentMethod, note,
          shippingAddress (đối chiếu lại với payload), items (tên hàng để in label)
   Lỗi gọi order-service → throw để Kafka retry / retry job xử lý sau.

2. RESOLVE ĐỊA CHỈ (self-map): (province, district, ward) text → GHN (province_id, district_id, ward_code)
   - Tra bảng location_mappings (khớp chính xác, rồi khớp gần đúng đã chuẩn hóa dấu/hoa-thường).
   - Không tìm ra → INSERT shipment status = PENDING, failure_reason = "ADDRESS_MAPPING_FAILED",
     KHÔNG gọi carrier, alert admin, kết thúc (retry job / admin retry sau khi sửa mapping).

3. XÁC ĐỊNH CARRIER + SERVICE
   - carrier = shipping.default-carrier (GHN).
   - serviceId: gọi GHN "get available services" (from district ↔ to district) → chọn gói phù hợp
     (rẻ nhất, hoặc theo cấu hình). Không lấy được → dùng carrier.ghn.default-service-id.

4. ƯỚC LƯỢNG GÓI HÀNG (tạm thời — chưa có weight thật)
   - weight  = SUM(item.qty) * shipping.default-item-weight-grams
   - length/width/height = shipping.default-package.*

5. TÍNH COD
   - codAmount = (paymentMethod == "COD") ? pricing.total : 0

6. GỌI API TẠO ĐƠN CỦA NHÀ VẬN CHUYỂN
   - GHN:  POST /v2/shipping-order/create → nhận order_code (= trackingCode)
   - GHTK: POST /services/shipment/order  → nhận label_id
   - Lỗi 5xx/timeout → Resilience4j retry; fail hẳn → INSERT shipment status = PENDING,
     failure_reason = "CARRIER_API_ERROR", retry job chạy lại mỗi 5 phút.

7. INSERT shipment (status = READY_TO_PICK, lưu trackingCode, shippingFee, codAmount,
   user_id = payload.userId, order_code = payload.orderCode, to_* = mã đã resolve)
8. INSERT shipment_tracking (status = READY_TO_PICK, description "Đơn hàng đã tạo, chờ lấy hàng")
9. Lưu shipping label PDF (nếu carrier trả)
10. publish Kafka shipment.updated (status = READY_TO_PICK)
11. SET Redis processed:event:{eventId} = "1" TTL 24h
```

---

### Consumer: order.cancelled → hủy vận đơn

**Topic:** `order.cancelled` · Message: JSON của `KafkaEvent<OrderCancelledPayload>`.

```json
{
  "eventId": "uuid-v4", "eventType": "order.cancelled", "timestamp": "...", "version": "1.0",
  "payload": {
    "orderId": "order-uuid-1",
    "reason": "PAYMENT_FAILED",
    "needReleaseStock": false,
    "items": [ { "variantId": "var-uuid-1", "qty": 2 } ]
  }
}
```

```
Consume order.cancelled:
0. IDEMPOTENCY: Redis processed:event:{eventId}.
1. Tìm shipment theo orderId. Không có (order bị hủy khi chưa kịp tạo vận đơn) → bỏ qua, coi như xong.
2. status = PENDING hoặc READY_TO_PICK → gọi carrier hủy đơn → shipment status = CANCELLED,
   INSERT shipment_tracking, publish shipment.updated (CANCELLED).
3. status = PICKED_UP / IN_TRANSIT → KHÔNG hủy được → giữ nguyên, ghi log, alert admin để xử lý hoàn hàng.
4. SET Redis processed:event:{eventId}.
```

---

### Publish: shipment.updated

**Topic:** `shipment.updated` · **Kafka key:** `orderId` · Envelope `KafkaEvent<T>` (giống order-service).

```json
{
  "eventId":   "uuid-v4",
  "eventType": "shipment.updated",
  "timestamp": "2024-01-17T09:00:00Z",
  "version":   "1.0",
  "payload": {
    "orderId":       "order-uuid-1",
    "shipmentId":    "ship-uuid-1",
    "trackingCode":  "GHN123456789",
    "carrier":       "GHN",
    "status":        "IN_TRANSIT",
    "description":   "Đang giao hàng",
    "estimatedDate": "2024-01-17"
  }
}
```

**Consumers:**
- **Order Service** — cập nhật order status theo shipment (xem PHẦN 4 — cần bổ sung).
- **Notification Service** — push notification ("Đơn đang giao", "Đã giao thành công").

---

## 6. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `SHIPMENT_NOT_FOUND` | 404 | Không tìm thấy vận đơn |
| `SHIPMENT_ACCESS_DENIED` | 403 | Vận đơn không thuộc user này |
| `SHIPMENT_CANNOT_CANCEL` | 409 | Đã lấy hàng, không hủy được |
| `ADDRESS_MAPPING_FAILED` | 422 | Không map được địa chỉ text sang mã nhà vận chuyển |
| `CARRIER_API_ERROR` | 502 | Lỗi từ API nhà vận chuyển |
| `INVALID_ADDRESS` | 400 | Địa chỉ không hợp lệ (sai mã district/ward khi client tự truyền) |
| `FEE_CALCULATION_FAILED` | 502 | Không tính được phí ship |
| `INVALID_WEBHOOK` | 400 | Webhook sai chữ ký |
| `ORDER_SERVICE_UNAVAILABLE` | 502 | Không gọi được `GET /internal/orders/{orderId}` để enrich |

---

## 7. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| POST | /shipping/calculate-fee | ✅ | USER |
| GET | /shipping/provinces | ❌ | — |
| GET | /shipping/districts | ❌ | — |
| GET | /shipping/wards | ❌ | — |
| POST | /internal/shipments | 🔒 Internal | — |
| GET | /shipments/order/{orderId} | ✅ | USER (chủ đơn) / ADMIN |
| GET | /shipments/{trackingCode}/track | ❌ | — |
| POST | /internal/shipments/{shipmentId}/cancel | 🔒 Internal | — |
| POST | /shipping/webhook/ghn | ❌ Webhook (chữ ký) | — |
| POST | /shipping/webhook/ghtk | ❌ Webhook (chữ ký) | — |
| GET | /admin/shipments | ✅ | ADMIN |
| POST | /admin/shipments/{id}/reprint-label | ✅ | ADMIN |
| POST | /admin/shipments/{id}/retry | ✅ | ADMIN |

---
---

# PHẦN 2 — DATABASE SCHEMA

---

## Bảng: shipments

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| order_id | UUID | NOT NULL, UNIQUE | Reference sang Order Service |
| order_code | VARCHAR(20) | NOT NULL | Snapshot mã đơn (từ payload `order.confirmed`) |
| user_id | UUID | NOT NULL | Chủ đơn — dùng để check quyền ở `GET /shipments/order/{orderId}` |
| carrier | VARCHAR(20) | NOT NULL | GHN / GHTK |
| service_id | VARCHAR(50) | NULLABLE | Mã dịch vụ của nhà vận chuyển — NULL khi shipment còn `PENDING` (chưa resolve) |
| tracking_code | VARCHAR(100) | NULLABLE, UNIQUE | Mã vận đơn — NULL cho tới khi tạo đơn thành công với carrier |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | State machine |
| to_name | VARCHAR(100) | NOT NULL | Người nhận |
| to_phone | VARCHAR(15) | NOT NULL | |
| to_address | TEXT | NOT NULL | `streetDetail` từ payload |
| to_province | VARCHAR(100) | NOT NULL | Snapshot tên (từ payload) |
| to_district | VARCHAR(100) | NOT NULL | Snapshot tên |
| to_ward | VARCHAR(100) | NOT NULL | Snapshot tên |
| to_province_id | INT | NULLABLE | Mã GHN đã resolve — NULL nếu map fail |
| to_district_id | INT | NULLABLE | Mã GHN đã resolve |
| to_ward_code | VARCHAR(20) | NULLABLE | Mã GHN đã resolve |
| weight | INT | NOT NULL | gram (ước lượng, xem config) |
| shipping_fee | BIGINT | NOT NULL | Phí ship — lấy từ `pricing.shippingFee` của Order Service |
| cod_amount | BIGINT | NOT NULL, DEFAULT 0 | `paymentMethod == COD ? pricing.total : 0` |
| insurance_value | BIGINT | NOT NULL, DEFAULT 0 | = `pricing.total` |
| payment_method | VARCHAR(20) | NOT NULL | Snapshot từ Order Service (VNPAY / MOMO / COD) |
| note | TEXT | NULLABLE | Ghi chú giao hàng (từ `orders.note`) |
| estimated_date | DATE | NULLABLE | Ngày dự kiến giao |
| shipping_label_url | TEXT | NULLABLE | Link PDF label |
| failure_reason | VARCHAR(50) | NULLABLE | `ADDRESS_MAPPING_FAILED` / `CARRIER_API_ERROR` khi status = PENDING |
| retry_count | INT | NOT NULL, DEFAULT 0 | Số lần retry job đã thử tạo đơn |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_shipments_order_id ON shipments(order_id);
CREATE UNIQUE INDEX idx_shipments_tracking_code ON shipments(tracking_code);
CREATE INDEX idx_shipments_status ON shipments(status);
CREATE INDEX idx_shipments_carrier ON shipments(carrier);
CREATE INDEX idx_shipments_user_id ON shipments(user_id);
```

**status state machine:**
```
PENDING ──► READY_TO_PICK ──► PICKED_UP ──► IN_TRANSIT ──► DELIVERED
   │              │                                    └──► FAILED ──► RETURNED
   │              │
   └──► CANCELLED ◄┘   (hủy khi chưa lấy hàng: từ PENDING hoặc READY_TO_PICK)

PENDING = đã ghi nhận nhưng chưa tạo được vận đơn với nhà vận chuyển
          (map địa chỉ fail hoặc carrier API lỗi) — retry job xử lý.
```

---

## Bảng: shipment_tracking

Lịch sử trạng thái giao hàng (timeline) — chỉ INSERT.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| shipment_id | UUID | NOT NULL, FK → shipments(id) ON DELETE CASCADE | |
| status | VARCHAR(20) | NOT NULL | Status nội bộ đã map |
| description | VARCHAR(255) | NULLABLE | Mô tả (VD: "Đang giao hàng") |
| location | VARCHAR(255) | NULLABLE | Vị trí (VD: "Kho GHN Q3") |
| carrier_status | VARCHAR(50) | NULLABLE | Status gốc từ nhà vận chuyển (chưa map) |
| happened_at | TIMESTAMP | NOT NULL | Thời điểm sự kiện xảy ra (từ nhà vận chuyển) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Thời điểm nhận webhook |

**Index:**
```sql
CREATE INDEX idx_shipment_tracking_shipment_id ON shipment_tracking(shipment_id);
CREATE INDEX idx_shipment_tracking_happened_at ON shipment_tracking(happened_at);
```

---

## Bảng: location_mappings — SELF-MAP ĐỊA CHỈ

Map địa chỉ text (đến từ Order Service / User Service) sang mã địa giới của nhà vận chuyển. Seed từ data GHN (`/shipping/provinces|districts|wards`), cập nhật định kỳ bằng job.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| province_name | VARCHAR(100) | NOT NULL | Tên gốc như GHN trả |
| district_name | VARCHAR(100) | NOT NULL | |
| ward_name | VARCHAR(100) | NOT NULL | |
| province_name_normalized | VARCHAR(100) | NOT NULL | Bỏ dấu, lowercase, bỏ tiền tố "TP."/"Tỉnh"/"Quận"/"Phường" — để khớp gần đúng |
| district_name_normalized | VARCHAR(100) | NOT NULL | |
| ward_name_normalized | VARCHAR(100) | NOT NULL | |
| ghn_province_id | INT | NOT NULL | |
| ghn_district_id | INT | NOT NULL | |
| ghn_ward_code | VARCHAR(20) | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_location_mappings_exact
  ON location_mappings(province_name, district_name, ward_name);
CREATE INDEX idx_location_mappings_normalized
  ON location_mappings(province_name_normalized, district_name_normalized, ward_name_normalized);
```

> GHTK dùng **tên** tỉnh/quận/xã trực tiếp (không cần mã) — lấy luôn từ `to_province/to_district/to_ward` snapshot trên `shipments`, chỉ cần đúng chính tả theo chuẩn GHTK.

---

## Bảng: processed_shipping_webhooks — IDEMPOTENCY

Chống xử lý trùng webhook từ nhà vận chuyển (họ cũng retry).

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| idempotency_key | VARCHAR(200) | NOT NULL, UNIQUE | Xem công thức bên dưới |
| carrier | VARCHAR(20) | NOT NULL | |
| processed_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**idempotency_key:**
```
"ghn:"  + trackingCode + ":" + status  + ":" + happenedAt
"ghtk:" + labelId      + ":" + statusId + ":" + actionTime
```

**Index:**
```sql
CREATE UNIQUE INDEX idx_processed_shipping_webhooks_key ON processed_shipping_webhooks(idempotency_key);
```

---

## Redis Keys — Shipping Service

| Key pattern | Value | TTL | Mục đích |
|-------------|-------|-----|---------|
| `shipping:fee:{fromDistrict}:{toDistrict}:{toWard}:{weight}` | JSON danh sách phí | 1 giờ | Cache phí ship |
| `shipping:provinces` | JSON list | 24 giờ | Cache tỉnh/thành |
| `shipping:districts:{provinceId}` | JSON list | 24 giờ | Cache quận/huyện |
| `shipping:wards:{districtId}` | JSON list | 24 giờ | Cache phường/xã |
| `shipping:track:{trackingCode}` | JSON `data` | 60 giây | Cache kết quả `GET /shipments/{trackingCode}/track` (public, chống spam) |
| `processed:event:{eventId}` | `"1"` | 24 giờ | Idempotency Kafka consumer (giống order-service) |

---

## Kafka — tổng hợp

| Chiều | Topic | Payload class (tương đương) |
|-------|-------|-----------------------------|
| Consume | `order.confirmed` | `KafkaEvent<OrderConfirmPayload>` (orderId, orderCode, userId, shippingAddress{fullName,phone,province,district,ward,streetDetail}, items[{variantId,qty}]) |
| Consume | `order.cancelled` | `KafkaEvent<OrderCancelledPayload>` (orderId, reason, needReleaseStock, items[{variantId,qty}]) |
| Publish | `shipment.updated` | `KafkaEvent<ShipmentUpdatedPayload>` (orderId, shipmentId, trackingCode, carrier, status, description, estimatedDate) |

---
---

# PHẦN 3 — TÍCH HỢP NHÀ VẬN CHUYỂN

---

## So sánh GHN vs GHTK

| | GHN (Giao Hàng Nhanh) | GHTK (Giao Hàng Tiết Kiệm) |
|---|---|---|
| Auth | Token trong header `Token` (+ `ShopId`) | Token trong header `Token` |
| Mã địa giới | province_id, district_id, ward_code riêng | Dùng tên tỉnh/quận/xã |
| Tính phí | POST /v2/shipping-order/fee | POST /services/shipment/fee |
| Tạo đơn | POST /v2/shipping-order/create | POST /services/shipment/order |
| Track | POST /v2/shipping-order/detail | GET /services/shipment/v2/{id} |
| Webhook | Cấu hình URL trên dashboard GHN | Cấu hình URL trên dashboard GHTK |
| Status | Dạng string (delivering, delivered) | Dạng số (status_id) |

---

## Vấn đề mã địa giới — Shipping Service tự map

```
Bối cảnh thực tế của project:
  - User Service (bảng user_addresses): province/district/ward là VARCHAR tên, KHÔNG có mã.
  - Order Service snapshot y hệt (bảng order_shipping_address) và phát event order.confirmed
    với địa chỉ dạng text.
  - GHN cần province_id / district_id / ward_code; GHTK cần đúng tên.

Giải pháp đang chọn: Shipping Service TỰ MAP
  1. Seed bảng location_mappings từ data GHN (/shipping/provinces|districts|wards).
  2. Khi tạo vận đơn: (province, district, ward) text → tra location_mappings
     - Khớp chính xác trước.
     - Không có → khớp theo *_normalized (bỏ dấu, bỏ tiền tố, lowercase).
     - Vẫn không có → shipment PENDING + failure_reason = ADDRESS_MAPPING_FAILED, alert admin.
  3. Admin bổ sung / sửa mapping → gọi POST /admin/shipments/{id}/retry.
```

> **Hướng cải tiến sau này (không làm bây giờ):** cho User Service lưu thêm `district_id` + `ward_code` khi user chọn địa chỉ qua dropdown (data từ Shipping Service), Order Service snapshot kèm 2 mã đó và đưa vào `order.confirmed`. Khi đó bỏ được bảng `location_mappings` và bước resolve. Hiện tại giữ self-map để không phải sửa User/Order Service.

---

## Retry khi API nhà vận chuyển lỗi

```
Dùng Resilience4j cho mọi call sang GHN/GHTK:
  - Retry: 3 lần, backoff 2s
  - Circuit Breaker: GHN lỗi liên tục → mở mạch (các shipment mới rơi về PENDING)
  - Timeout: 10s / call

Scheduled job "retry tạo vận đơn":
  - Chạy mỗi 5 phút
  - SELECT * FROM shipments WHERE status = 'PENDING' AND retry_count < 5
  - Với mỗi cái: chạy lại "Flow tạo vận đơn" từ bước 2 (resolve địa chỉ → tạo đơn carrier)
  - retry_count++; >= 5 → giữ PENDING, alert admin
```

---

## Đối soát COD

```
Với đơn COD, nhà vận chuyển thu tiền hộ rồi chuyển lại cho shop.
  1. Nhà vận chuyển gửi report tiền COD đã thu (định kỳ)
  2. So sánh với shipments có payment_method = COD và status = DELIVERED
  3. Đánh dấu shipment nào đã nhận được tiền COD
  4. Alert nếu có shipment DELIVERED nhưng chưa nhận COD sau X ngày
```

> Order Service riêng nó vẫn tự gọi `PATCH /internal/payments/{paymentId}/confirm-cod` bên Payment Service khi order sang `DELIVERED` (xem orderServiceApiSpec.md mục 5) — đối soát ở Shipping Service chỉ để phát hiện lệch tiền, không thay thế bước đó.

---
---

# PHẦN 4 — TÍCH HỢP PHÍA ORDER SERVICE (CẦN BỔ SUNG)

Order Service hiện **chưa** có gì liên quan Shipping (do service này chưa tồn tại lúc đó). Để luồng chạy đủ, order-service cần thêm:

### 1. Consumer `shipment.updated`

```java
@KafkaListener(topics = "shipment.updated", groupId = "order-service")
```
Deserialize `KafkaEvent<ShipmentUpdatedPayload>` (giống các listener hiện có: nhận `String`, `objectMapper.readValue`). Idempotency qua `processed:event:{eventId}`.

**Map shipment status → order status** (chỉ tiến theo state machine `orderServiceApiSpec.md` mục 5, bỏ qua transition lùi / không hợp lệ):

| shipment.status | order.status sau khi xử lý | Ghi chú |
|---|---|---|
| `READY_TO_PICK` | `CONFIRMED → PROCESSING` | Vận đơn đã tạo, shop chuẩn bị hàng |
| `PICKED_UP` | `PROCESSING → SHIPPING` | Nhà vận chuyển đã lấy hàng |
| `IN_TRANSIT` | `SHIPPING` (giữ nguyên) | Chỉ ghi timeline |
| `DELIVERED` | `SHIPPING → DELIVERED` | + nếu `payment_method = COD` → gọi `PATCH /internal/payments/{paymentId}/confirm-cod` |
| `FAILED` / `RETURNED` | giữ nguyên | Ghi `order_status_history`, alert admin xử lý tay |
| `CANCELLED` | không đổi | Order đã tự CANCELLED qua luồng hủy của nó rồi |

> 2 transition `CONFIRMED→PROCESSING→SHIPPING→DELIVERED` hiện chỉ cho phép qua `PATCH /admin/orders/{orderId}/status`. Khi thêm consumer này, cho phép **thêm nguồn** `changed_by = SYSTEM` với đúng các bước ngang đó (không đụng tới `PENDING→CONFIRMED` và `*→CANCELLED`).

### 2. Không cần thay đổi gì khác

- `order.confirmed` / `order.cancelled` giữ nguyên payload hiện tại — Shipping Service đã thiết kế để tự enrich phần thiếu qua `GET /internal/orders/{orderId}` (đã tồn tại).
- `GET /internal/orders/{orderId}` giữ nguyên `OrderDetailResponse` — đã đủ (`pricing.shippingFee`, `pricing.total`, `paymentMethod`, `note`, `shippingAddress`, `items`).

---
---

# PHẦN 5 — CARRIER ABSTRACTION (GHN / GHTK)

Toàn bộ code service **không gọi thẳng** GHN/GHTK. Mọi thứ đi qua 1 interface `CarrierClient`. Nhờ đó:
- Dev & test không cần token thật (dùng `MockCarrierClient`).
- Thêm/bớt nhà vận chuyển không đụng business logic.
- Chuẩn hóa request/response về 1 bộ DTO nội bộ, phần status vocab khác nhau xử lý gọn trong từng impl.

---

## 1. Cấu hình chọn implementation

```properties
# mock  = MockCarrierClient (mặc định — không gọi mạng, trả dữ liệu cắm sẵn)
# real  = gọi GHN/GHTK thật (cần GHN_TOKEN / GHN_SHOP_ID / GHTK_TOKEN)
carrier.mode=mock
```

`CarrierClientFactory` chọn impl theo `carrier.mode` + `carrier` (GHN/GHTK) của shipment.
`carrier.mode=mock` → luôn trả về `MockCarrierClient` bất kể carrier nào.

---

## 2. Interface `CarrierClient`

```java
public interface CarrierClient {

    CarrierType carrierType();                       // GHN | GHTK

    List<FeeQuote> calculateFee(FeeRequest req);     // nhiều gói dịch vụ của hãng đó

    String resolveServiceId(ResolveServiceRequest req); // gói phù hợp nhất (rẻ nhất) cho tuyến from→to

    CreateOrderResult createOrder(CreateOrderRequest req);

    List<TrackingEvent> getTracking(String trackingCode);

    void cancelOrder(String trackingCode);          // ném CarrierCannotCancelException nếu đã lấy hàng

    String getLabelUrl(String trackingCode);        // link PDF label (reprint)
}
```

### DTO nội bộ (chuẩn hóa — không lệ thuộc GHN/GHTK)

```java
// ----- Tính phí -----
record FeeRequest(
    int fromDistrictId, String fromWardCode,
    int toDistrictId,   String toWardCode,
    String toProvinceName, String toDistrictName, String toWardName, // GHTK dùng tên
    int weightGram, int lengthCm, int widthCm, int heightCm,
    long insuranceValue
) {}

record FeeQuote(
    CarrierType carrier,
    String serviceId,        // luôn String
    String serviceName,
    long fee,
    int estimatedDays,
    LocalDate estimatedDate
) {}

// ----- Resolve service -----
record ResolveServiceRequest(int fromDistrictId, int toDistrictId, int weightGram) {}

// ----- Tạo đơn -----
record CreateOrderRequest(
    String orderCode,
    String serviceId,
    Recipient to,                 // name, phone, address, provinceName, districtName, wardName,
                                  // districtId (GHN), wardCode (GHN)
    int weightGram, int lengthCm, int widthCm, int heightCm,
    long codAmount,
    long insuranceValue,
    String note,
    List<ItemLine> items          // name, quantity, weightGram
) {}

record CreateOrderResult(
    String trackingCode,
    ShipmentStatus status,        // thường READY_TO_PICK
    LocalDate estimatedDate,
    String labelUrl               // nullable
) {}

// ----- Tracking -----
record TrackingEvent(
    ShipmentStatus status,        // đã map về status nội bộ
    String carrierStatus,         // giữ nguyên gốc ("delivering" / "3")
    String description,
    String location,
    Instant happenedAt
) {}
```

> `ShipmentStatus` = enum nội bộ: `PENDING, READY_TO_PICK, PICKED_UP, IN_TRANSIT, DELIVERED, FAILED, RETURNED, CANCELLED`.

---

## 3. `MockCarrierClient` — DÙNG BÂY GIỜ

Mục tiêu: chạy full luồng (Kafka consume → tạo shipment → publish `shipment.updated` → order-service đổi status) mà không cần mạng.

| Method | Hành vi mock |
|---|---|
| `calculateFee` | Trả 2 `FeeQuote` cắm sẵn: `{serviceId:"MOCK_FAST", fee:30000, days:2}`, `{serviceId:"MOCK_STD", fee:22000, days:4}`. `estimatedDate = today + days`. |
| `resolveServiceId` | Trả `"MOCK_STD"`. |
| `createOrder` | Sinh `trackingCode = "MOCK" + 9 số random`. Trả `status = READY_TO_PICK`, `estimatedDate = today + 3`, `labelUrl = "https://mock.local/labels/" + trackingCode + ".pdf"`. Không lưu gì bên ngoài. |
| `getTracking` | Đọc `shipment_tracking` trong DB của chính mình rồi trả về (không có "carrier" thật để hỏi). |
| `cancelOrder` | Không làm gì (no-op). Nếu muốn test nhánh lỗi: nếu `trackingCode` kết thúc bằng số chẵn → ném `CarrierCannotCancelException`. |
| `getLabelUrl` | Trả `"https://mock.local/labels/" + trackingCode + ".pdf"`. |

### Giả lập trạng thái giao hàng (thay cho webhook thật)

`carrier.mode=mock` thì GHN/GHTK không bao giờ gọi webhook. Có 2 cách đẩy shipment đi tiếp:

**Cách A — gọi tay endpoint webhook** (khuyến khích, test luôn code parse webhook):
```bash
curl -X POST http://localhost:8087/api/v1/shipping/webhook/ghn \
  -H "Content-Type: application/json" \
  -H "X-GHN-Signature: mock" \
  -d '{"orderCode":"MOCK123456789","status":"delivering",
       "description":"Đang giao","warehouse":"Kho GHN Q3","time":"2026-08-27T09:00:00Z"}'
```
> Khi `carrier.mode=mock`: bỏ qua verify chữ ký webhook (chỉ check header tồn tại).

**Cách B — scheduler mô phỏng** (bật bằng `carrier.mock.auto-advance=true`, mặc định `false`):
mỗi 60s quét shipment chưa `DELIVERED`/`CANCELLED` và đẩy sang bước kế tiếp:
`READY_TO_PICK → PICKED_UP → IN_TRANSIT → DELIVERED`. Tiện demo end-to-end không cần curl.

---

## 4. Bản đồ endpoint & mapping cho `GhnClient` / `GhtkClient` (LÀM SAU)

Chỉ implement khi đã có token. Mỗi impl chịu trách nhiệm: gắn header auth, đổi DTO nội bộ ↔ payload hãng, map status hãng → `ShipmentStatus`.

### GHN — header

```
Token:   {carrier.ghn.token}
ShopId:  {carrier.ghn.shop-id}
Content-Type: application/json
```

| Interface method | GHN endpoint | Ghi chú map |
|---|---|---|
| `calculateFee` | `POST /v2/shipping-order/fee` | cần `service_id` hoặc `service_type_id`; gọi kèm `available-services` để lấy list |
| `resolveServiceId` | `POST /v2/shipping-order/available-services` | body `{shop_id, from_district, to_district}` → lấy phần tử đầu / rẻ nhất |
| `createOrder` | `POST /v2/shipping-order/create` | trả `data.order_code` → `trackingCode`; `data.expected_delivery_time` → `estimatedDate` |
| `getTracking` | `POST /v2/shipping-order/detail` | `data.log[]` → `List<TrackingEvent>` |
| `cancelOrder` | `POST /v2/switch-status/cancel` | body `{order_codes:[trackingCode]}`; đã lấy hàng → GHN trả lỗi → ném `CarrierCannotCancelException` |
| `getLabelUrl` | `GET /v2/a5/gen-token` → `POST /a5/public-api/printA5` | hoặc dựng URL `https://dev-online-gateway.ghn.vn/a5/public-api/printA5?token=...` |

**Map status GHN → nội bộ:**
```
ready_to_pick, picking             → READY_TO_PICK
picked, storing, transporting...   → PICKED_UP
delivering, sorting, ...           → IN_TRANSIT
delivered                          → DELIVERED
delivery_fail, return...           → FAILED
returned, returning                → RETURNED
cancel                             → CANCELLED
```
> Danh sách status GHN dài (~30 giá trị) — gom nhóm theo bảng trên, giá trị lạ → log + coi như `IN_TRANSIT`, không ném lỗi.

### GHTK — header

```
Token: {carrier.ghtk.token}
```

| Interface method | GHTK endpoint | Ghi chú map |
|---|---|---|
| `calculateFee` | `POST /services/shipment/fee` | tham số bằng **tên** tỉnh/quận/xã, không dùng id |
| `resolveServiceId` | — | GHTK không có khái niệm service_id rõ ràng → trả cố định `"standard"` (đường bộ) hoặc `"express"` |
| `createOrder` | `POST /services/shipment/order` | trả `order.label` → `trackingCode`; `order.estimated_deliver_time` → `estimatedDate` |
| `getTracking` | `GET /services/shipment/v2/{trackingCode}` | |
| `cancelOrder` | `POST /services/shipment/cancel/{trackingCode}` | |
| `getLabelUrl` | `GET /services/label/{trackingCode}` | trả file PDF trực tiếp → lưu ra storage rồi trả URL |

**Map status_id GHTK → nội bộ:**
```
-1        → CANCELLED
1, 2      → READY_TO_PICK
3         → PICKED_UP
4, 5      → IN_TRANSIT      (đang luân chuyển / đang giao)
5? tuỳ    → xem tài liệu; 45 = đã giao → DELIVERED
6, 9      → FAILED
10, 11    → RETURNED
```
> Bảng status_id GHTK phải đối chiếu tài liệu chính thức khi implement — giá trị ở đây chỉ là khung.

---

## 5. Thứ tự làm đề xuất

```
1. Entities + repos + Flyway (shipments, shipment_tracking, location_mappings, processed_shipping_webhooks)
2. CarrierClient interface + DTO nội bộ + MockCarrierClient + CarrierClientFactory
3. Consumer order.confirmed  → flow tạo vận đơn (dùng Mock)  → publish shipment.updated
4. Webhook controller (ghn/ghtk) + idempotency  → test bằng curl (Cách A)
5. Consumer order.cancelled  → cancel shipment
6. REST: calculate-fee, /shipments/order/{orderId}, /track, admin/*
7. location_mappings seed job (gọi GHN master-data)  → lúc này mới cần GHN_TOKEN
8. GhnClient thật  (carrier.mode=real, GHN sandbox)
9. GhtkClient  — cuối cùng, hoặc bỏ nếu không xin được token
```

Từ bước 1–6 chạy hoàn chỉnh với `carrier.mode=mock`, không cần đăng ký gì với GHN/GHTK.
