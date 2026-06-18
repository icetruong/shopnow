# Cart Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8084/api/v1
```

## Đặc điểm của Cart Service
- **KHÔNG có database riêng** — toàn bộ cart data lưu trong Redis
- **Stateless** — mỗi request đọc/ghi thẳng vào Redis
- Gọi **Product Service** để lấy thông tin sản phẩm
- Gọi **Inventory Service** để kiểm tra tồn kho

---

# PHẦN 1 — API ENDPOINTS

---

## 1. CART — Giỏ hàng

---

### GET /cart
Lấy giỏ hàng hiện tại của user. Đồng thời kiểm tra tồn kho realtime và đánh dấu item nào hết hàng.

**Header:** `Authorization: Bearer {accessToken}`

**Response 200**
```json
{
  "success": true,
  "message": "Lấy giỏ hàng thành công.",
  "data": {
    "cartId":    "cart:user-uuid-1",
    "userId":    "user-uuid-1",
    "items": [
      {
        "cartItemId":   "item-uuid-1",
        "variantId":    "var-uuid-1",
        "productId":    "prod-uuid-1",
        "productName":  "Áo Polo Nam Basic",
        "productSlug":  "ao-polo-nam-basic",
        "thumbnail":    "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "color":        "Trắng",
        "size":         "S",
        "sku":          "POLO-WHITE-S",
        "unitPrice":    249000,
        "qty":          2,
        "subtotal":     498000,
        "stockStatus":  "IN_STOCK",
        "availableQty": 45,
        "isAvailable":  true,
        "addedAt":      "2024-01-15T10:00:00Z"
      },
      {
        "cartItemId":   "item-uuid-2",
        "variantId":    "var-uuid-3",
        "productId":    "prod-uuid-1",
        "productName":  "Áo Polo Nam Basic",
        "productSlug":  "ao-polo-nam-basic",
        "thumbnail":    "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "color":        "Xanh navy",
        "size":         "S",
        "sku":          "POLO-BLUE-S",
        "unitPrice":    249000,
        "qty":          1,
        "subtotal":     249000,
        "stockStatus":  "OUT_OF_STOCK",
        "availableQty": 0,
        "isAvailable":  false,
        "addedAt":      "2024-01-14T09:00:00Z"
      }
    ],
    "summary": {
      "totalItems":      3,
      "totalUniqueItems":2,
      "subtotal":        747000,
      "hasUnavailableItems": true
    },
    "updatedAt": "2024-01-15T10:00:00Z"
  }
}
```

**Flow bên trong:**
```
1. Đọc cart từ Redis (key: cart:{userId})
2. Gọi Inventory Service batch để kiểm tra stock tất cả variantId trong cart
3. Gọi Product Service batch để lấy giá hiện tại (giá có thể thay đổi)
4. Merge data: gắn stockStatus + giá mới nhất vào từng item
5. Trả về — KHÔNG ghi lại vào Redis ở bước này
```

**Lưu ý:** Giá trong cart (`unitPrice`) luôn lấy giá mới nhất từ Product Service mỗi lần GET — không cache giá trong Redis để tránh giá cũ.

---

### POST /cart/items
Thêm sản phẩm vào giỏ hàng.

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "variantId": "var-uuid-1",
  "qty":       2
}
```

**Validation**
- `variantId`: không trống, phải tồn tại
- `qty`: 1–99

**Response 200** — thêm thành công (hoặc tăng qty nếu đã có trong giỏ)
```json
{
  "success": true,
  "message": "Đã thêm vào giỏ hàng.",
  "data": {
    "cartItemId": "item-uuid-1",
    "variantId":  "var-uuid-1",
    "qty":        2,
    "totalItems": 3
  }
}
```

**Response 409** — hết hàng
```json
{
  "success": false,
  "code":    "OUT_OF_STOCK",
  "message": "Sản phẩm này hiện đã hết hàng."
}
```

**Response 409** — không đủ số lượng
```json
{
  "success": false,
  "code":    "INSUFFICIENT_STOCK",
  "message": "Chỉ còn 3 sản phẩm trong kho.",
}
```

**Flow bên trong:**
```
1. Gọi Product Service: kiểm tra variant tồn tại + isActive
2. Gọi Inventory Service: kiểm tra availableQty
3. Nếu availableQty = 0 → trả 409 OUT_OF_STOCK
4. Nếu qty yêu cầu > availableQty → trả 409 INSUFFICIENT_STOCK
5. Đọc cart hiện tại từ Redis
6. Nếu variantId đã có trong cart → qty += qty mới (nhưng không vượt availableQty)
7. Nếu chưa có → thêm item mới với cartItemId = UUID mới
8. SETEX cart:{userId} 604800 {cartJSON}   (TTL 7 ngày, reset mỗi lần có thao tác)
9. Trả về response
```

---

### PUT /cart/items/{cartItemId}
Cập nhật số lượng của 1 item trong giỏ.

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "qty": 3
}
```

**Validation:** `qty` từ 1–99. Nếu muốn xóa thì gọi DELETE.

**Response 200**
```json
{
  "success": true,
  "data": {
    "cartItemId": "item-uuid-1",
    "qty":        3,
    "subtotal":   747000,
    "totalItems": 4
  }
}
```

**Response 409** — qty yêu cầu vượt tồn kho
```json
{
  "success": false,
  "code":    "INSUFFICIENT_STOCK",
  "message": "Chỉ còn 2 sản phẩm trong kho.",
  "data": {
    "availableQty": 2
  }
}
```

**Flow bên trong:**
```
1. Đọc cart từ Redis, tìm item theo cartItemId
2. Nếu không tìm thấy → 404
3. Gọi Inventory Service: kiểm tra availableQty của variantId đó
4. Nếu qty mới > availableQty → 409
5. UPDATE qty trong cart JSON
6. SETEX lại Redis với TTL reset 7 ngày
```

---

### DELETE /cart/items/{cartItemId}
Xóa 1 item khỏi giỏ hàng.

**Header:** `Authorization: Bearer {accessToken}`

**Response 200**
```json
{
  "success":    true,
  "message":    "Đã xóa sản phẩm khỏi giỏ hàng.",
  "totalItems": 2
}
```

---

### DELETE /cart
Xóa toàn bộ giỏ hàng.

**Header:** `Authorization: Bearer {accessToken}`

**Response 200**
```json
{
  "success": true,
  "message": "Đã xóa giỏ hàng."
}
```

**Logic:** `DEL cart:{userId}`

---

### POST /cart/select
Chọn / bỏ chọn item để checkout (user có thể chọn 1 phần giỏ hàng).

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "cartItemIds": ["item-uuid-1", "item-uuid-2"],
  "selected":    true
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã cập nhật lựa chọn."
}
```

**Logic:** Ghi thêm field `selected: true/false` vào từng item trong cart JSON trên Redis.

---

## 2. CHECKOUT — Kiểm tra trước khi đặt hàng

---

### POST /cart/checkout/validate
Validate toàn bộ giỏ hàng trước khi chuyển sang trang thanh toán.
**Đây là bước quan trọng nhất** — kiểm tra tất cả item còn hàng, giá đúng, coupon hợp lệ.

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "cartItemIds": ["item-uuid-1"],
  "couponCode":  "SALE10",
  "addressId":   "addr-uuid-1"
}
```

**Response 200** — hợp lệ, sẵn sàng checkout
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "cartItemId":  "item-uuid-1",
        "variantId":   "var-uuid-1",
        "productName": "Áo Polo Nam Basic",
        "color":       "Trắng",
        "size":        "S",
        "unitPrice":   249000,
        "qty":         2,
        "subtotal":    498000,
        "isAvailable": true
      }
    ],
    "coupon": {
      "code":          "SALE10",
      "discountType":  "PERCENTAGE",
      "discountValue": 10,
      "discountAmt":   49800,
      "isValid":       true
    },
    "pricing": {
      "subtotal":      498000,
      "discount":      49800,
      "shippingFee":   0,
      "total":         448200
    },
    "checkoutToken": "checkout-token-uuid",
    "expiresAt":     "2024-01-15T10:45:00Z"
  }
}
```

**Response 400** — có item hết hàng hoặc giá thay đổi
```json
{
  "success": false,
  "code":    "CART_VALIDATION_FAILED",
  "message": "Giỏ hàng có thay đổi, vui lòng kiểm tra lại.",
  "data": {
    "issues": [
      {
        "cartItemId":  "item-uuid-2",
        "type":        "OUT_OF_STOCK",
        "message":     "Áo Polo Xanh navy size S đã hết hàng."
      },
      {
        "cartItemId":  "item-uuid-3",
        "type":        "PRICE_CHANGED",
        "oldPrice":    200000,
        "newPrice":    249000,
        "message":     "Giá sản phẩm đã thay đổi."
      },
      {
        "cartItemId":  "item-uuid-4",
        "type":        "QTY_REDUCED",
        "requestedQty":5,
        "availableQty":2,
        "message":     "Chỉ còn 2 sản phẩm trong kho."
      }
    ]
  }
}
```

**Flow bên trong (quan trọng nhất):**
```
1. Lấy các item được chọn từ Redis cart
2. Gọi Product Service BATCH: lấy giá + isActive của tất cả variant
3. Gọi Inventory Service BATCH: lấy availableQty của tất cả variant
4. Validate từng item:
   a. Product isActive = false      → issue: PRODUCT_UNAVAILABLE
   b. availableQty = 0              → issue: OUT_OF_STOCK
   c. qty > availableQty            → issue: QTY_REDUCED (giảm qty xuống)
   d. currentPrice != cartPrice     → issue: PRICE_CHANGED (update giá mới)
5. Nếu có bất kỳ issue nào → trả 400 kèm danh sách issues
6. Nếu có couponCode → gọi Promotion Service: validate coupon
7. Nếu tất cả hợp lệ:
   a. Tạo checkoutToken = UUID
   b. Lưu vào Redis: checkout:{checkoutToken} = {validated cart data} TTL 15 phút
   c. Trả về checkoutToken cho client dùng ở bước tạo order
```

**Tại sao cần `checkoutToken`?**
Để Order Service dùng token này lấy cart data đã validate — tránh validate lại lần nữa và tránh user thay đổi cart giữa chừng khi đang thanh toán.

---

### POST /cart/checkout/confirm
Order Service gọi internal sau khi tạo order thành công để xóa các item đã đặt khỏi giỏ.

**Header:** `X-Internal-Token: {sharedSecret}`

**Request Body**
```json
{
  "userId":        "user-uuid-1",
  "cartItemIds":   ["item-uuid-1"],
  "checkoutToken": "checkout-token-uuid"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã xóa items đã đặt khỏi giỏ hàng."
}
```

**Logic:**
```
1. Validate checkoutToken còn hợp lệ trong Redis
2. Xóa các cartItemIds được chọn khỏi cart:{userId}
3. DEL checkout:{checkoutToken}
4. Nếu cart rỗng → DEL cart:{userId} luôn
```

---

## 3. INTERNAL

---

### GET /internal/cart/checkout/{checkoutToken}
Order Service gọi để lấy cart data đã validate khi tạo order.

**Header:** `X-Internal-Token: {sharedSecret}`

**Response 200**
```json
{
  "userId":  "user-uuid-1",
  "items": [
    {
      "variantId":  "var-uuid-1",
      "productId":  "prod-uuid-1",
      "sku":        "POLO-WHITE-S",
      "unitPrice":  249000,
      "qty":        2,
      "subtotal":   498000
    }
  ],
  "couponCode":  "SALE10",
  "discountAmt": 49800,
  "total":       448200,
  "validatedAt": "2024-01-15T10:30:00Z",
  "expiresAt":   "2024-01-15T10:45:00Z"
}
```

**Response 404** — token hết hạn hoặc không hợp lệ
```json
{
  "success": false,
  "code":    "CHECKOUT_TOKEN_EXPIRED",
  "message": "Phiên thanh toán đã hết hạn. Vui lòng thử lại."
}
```

---

## 4. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `OUT_OF_STOCK` | 409 | Sản phẩm hết hàng |
| `INSUFFICIENT_STOCK` | 409 | Không đủ số lượng yêu cầu |
| `CART_ITEM_NOT_FOUND` | 404 | CartItemId không tồn tại trong giỏ |
| `CART_EMPTY` | 400 | Giỏ hàng rỗng khi checkout |
| `CART_VALIDATION_FAILED` | 400 | Có item hết hàng / giá thay đổi |
| `CHECKOUT_TOKEN_EXPIRED` | 404 | Token checkout hết hạn (15 phút) |
| `PRODUCT_UNAVAILABLE` | 409 | Sản phẩm đã bị ẩn / ngừng bán |
| `MAX_QTY_EXCEEDED` | 400 | qty vượt quá 99 |

---

## 5. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| GET | /cart | ✅ | USER |
| POST | /cart/items | ✅ | USER |
| PUT | /cart/items/{cartItemId} | ✅ | USER |
| DELETE | /cart/items/{cartItemId} | ✅ | USER |
| DELETE | /cart | ✅ | USER |
| POST | /cart/select | ✅ | USER |
| POST | /cart/checkout/validate | ✅ | USER |
| POST | /cart/checkout/confirm | 🔒 Internal | — |
| GET | /internal/cart/checkout/{token} | 🔒 Internal | — |

---

---

# PHẦN 2 — REDIS DATA STRUCTURE

Cart Service không có database — toàn bộ data sống trong Redis.

---

## Cart Data Structure

```
Key:   cart:{userId}
Type:  String (JSON)
TTL:   604800 giây (7 ngày) — reset mỗi lần user thao tác
```

**JSON structure lưu trong Redis:**
```json
{
  "userId":    "user-uuid-1",
  "updatedAt": "2024-01-15T10:00:00Z",
  "items": {
    "item-uuid-1": {
      "cartItemId": "item-uuid-1",
      "variantId":  "var-uuid-1",
      "productId":  "prod-uuid-1",
      "sku":        "POLO-WHITE-S",
      "qty":        2,
      "selected":   true,
      "addedAt":    "2024-01-15T10:00:00Z"
    },
    "item-uuid-2": {
      "cartItemId": "item-uuid-2",
      "variantId":  "var-uuid-3",
      "productId":  "prod-uuid-1",
      "sku":        "POLO-BLUE-S",
      "qty":        1,
      "selected":   false,
      "addedAt":    "2024-01-14T09:00:00Z"
    }
  }
}
```

**Tại sao dùng Map (items là object) thay vì Array?**
Tìm item theo `cartItemId` là O(1) thay vì O(n). Khi update/delete 1 item không cần loop cả mảng.

**Lưu ý những gì KHÔNG lưu trong Redis:**
- Tên sản phẩm, ảnh, màu, size → lấy từ Product Service mỗi lần GET
- Giá → lấy từ Product Service mỗi lần GET (luôn giá mới nhất)
- Tồn kho → lấy từ Inventory Service mỗi lần GET

---

## Checkout Token Structure

```
Key:   checkout:{checkoutToken}
Type:  String (JSON)
TTL:   900 giây (15 phút)
```

```json
{
  "checkoutToken": "checkout-token-uuid",
  "userId":        "user-uuid-1",
  "items": [
    {
      "cartItemId": "item-uuid-1",
      "variantId":  "var-uuid-1",
      "productId":  "prod-uuid-1",
      "sku":        "POLO-WHITE-S",
      "unitPrice":  249000,
      "qty":        2,
      "subtotal":   498000
    }
  ],
  "couponCode":   "SALE10",
  "discountAmt":  49800,
  "shippingFee":  0,
  "total":        448200,
  "addressId":    "addr-uuid-1",
  "validatedAt":  "2024-01-15T10:30:00Z",
  "expiresAt":    "2024-01-15T10:45:00Z"
}
```

---

## Tất cả Redis Keys của Cart Service

| Key pattern | Type | TTL | Mục đích |
|-------------|------|-----|---------|
| `cart:{userId}` | String (JSON) | 7 ngày | Giỏ hàng của user |
| `checkout:{checkoutToken}` | String (JSON) | 15 phút | Dữ liệu checkout đã validate |

---

# PHẦN 3 — XỬ LÝ HẾT HÀNG KHI CHECKOUT

Đây là flow phức tạp nhất, cần xử lý đúng để không làm khó user.

---

## Các tình huống hết hàng

```
Tình huống 1 — Hết hoàn toàn (availableQty = 0)
  → Đánh dấu isAvailable = false
  → Hiển thị "Hết hàng" trên UI
  → Không cho checkout item này
  → Gợi ý user xóa khỏi giỏ hoặc lưu vào wishlist

Tình huống 2 — Không đủ số lượng (qty > availableQty)
  → Tự động giảm qty xuống bằng availableQty
  → Thông báo "Chỉ còn X sản phẩm, đã điều chỉnh số lượng"
  → Vẫn cho checkout với qty đã điều chỉnh

Tình huống 3 — Giá thay đổi (unitPrice != currentPrice)
  → Cập nhật giá mới vào response
  → Thông báo "Giá sản phẩm đã thay đổi từ X → Y"
  → Vẫn cho checkout với giá mới

Tình huống 4 — Sản phẩm bị ẩn (isActive = false)
  → Đánh dấu isAvailable = false
  → Thông báo "Sản phẩm không còn được bán"
  → Không cho checkout
```

---

## Race condition khi checkout

```
Vấn đề: User A và User B cùng checkout lúc kho còn 1 cái
  → Cả 2 đều qua bước validate OK (availableQty = 1)
  → Cả 2 cùng gọi Order Service tạo đơn
  → Inventory Service mới phát hiện không đủ hàng

Giải pháp:
  → Cart Service chỉ KIỂM TRA tồn kho (không lock)
  → Inventory Service mới là nơi THẬT SỰ LOCK và trừ hàng
  → Nếu Inventory Service từ chối → Order Service rollback Saga
  → Trả lỗi về cho user: "Rất tiếc, sản phẩm vừa hết hàng"
```

**Đây là lý do Cart Service và Inventory Service phải tách nhau** — Cart chỉ validate nhanh để UX tốt, còn lock thật sự phải ở Inventory Service với Pessimistic Lock.

---

## Flow hoàn chỉnh từ Cart đến Order

```
1. User chọn item → POST /cart/checkout/validate
      ↓ Kiểm tra stock + giá + coupon
      ↓ Nếu OK → trả checkoutToken (TTL 15 phút)

2. User xác nhận đặt → POST /orders (Order Service)
      ↓ Order Service gọi GET /internal/cart/checkout/{checkoutToken}
      ↓ Lấy cart data đã validate

3. Order Service tạo order trong DB (status = PENDING)
      ↓ Publish Kafka event order.created

4. Inventory Service consume order.created
      ↓ Pessimistic Lock + kiểm tra stock lần cuối
      ↓ Nếu đủ hàng → reservedQty += qty → publish stock.reserved
      ↓ Nếu hết hàng → publish stock.insufficient

5. Order Service consume stock.reserved
      ↓ Gọi Payment Service
      ↓ Gọi POST /cart/checkout/confirm (xóa item khỏi cart)

6. Nếu stock.insufficient
      ↓ Order Service update status = FAILED
      ↓ Trả lỗi về user: "Sản phẩm vừa hết hàng"
      ↓ User quay lại cart, item đã được đánh dấu OUT_OF_STOCK
```