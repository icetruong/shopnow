# Review Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8090/api/v1
```

## Vai trò
Review Service quản lý đánh giá (rating + comment) của user cho sản phẩm, kiểm duyệt nội dung (moderation), và tính điểm trung bình. Sau mỗi review, nó publish event để Product Service cập nhật rating hiển thị.

## Nguyên tắc quan trọng
- **Chỉ user đã mua hàng mới được review** (verified purchase) — kiểm tra qua Order Service
- Mỗi user chỉ review 1 lần cho mỗi variant đã mua trong 1 đơn
- Review có thể bị ẩn nếu vi phạm (moderation)

---

# PHẦN 1 — API ENDPOINTS

---

## 1. REVIEW — Đánh giá sản phẩm

---

### POST /reviews
User tạo đánh giá cho sản phẩm đã mua.

**Header:** `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "orderId":   "order-uuid-1",
  "productId": "prod-uuid-1",
  "variantId": "var-uuid-1",
  "rating":    5,
  "comment":   "Áo đẹp, chất vải mát, đúng size. Shop giao nhanh!",
  "images": [
    "https://storage.shopnow.com/reviews/img1.jpg",
    "https://storage.shopnow.com/reviews/img2.jpg"
  ]
}
```

**Validation**
- `rating`: bắt buộc, 1–5
- `comment`: optional, tối đa 1000 ký tự
- `images`: optional, tối đa 5 ảnh
- `orderId`: phải là đơn của user này, status = DELIVERED/COMPLETED
- Chưa review variant này trong đơn này

**Response 201**
```json
{
  "success": true,
  "message": "Cảm ơn bạn đã đánh giá!",
  "data": {
    "reviewId":  "review-uuid-1",
    "status":    "PENDING",
    "createdAt": "2024-01-20T10:00:00Z"
  }
}
```

**Response 403** — chưa mua hàng
```json
{
  "success": false,
  "code":    "PURCHASE_REQUIRED",
  "message": "Bạn cần mua sản phẩm này trước khi đánh giá."
}
```

**Response 409** — đã review rồi
```json
{
  "success": false,
  "code":    "ALREADY_REVIEWED",
  "message": "Bạn đã đánh giá sản phẩm này trong đơn hàng này."
}
```

**Flow bên trong:**
```
1. Gọi Order Service: GET /internal/orders/{orderId}
   → Verify order thuộc về user + status = DELIVERED/COMPLETED
   → Verify variantId có trong order items
2. Check chưa review (unique: userId + orderId + variantId)
3. Chạy auto-moderation (filter từ cấm)
   → Nếu có từ cấm → status = PENDING (chờ admin duyệt)
   → Nếu sạch → status = APPROVED luôn
4. INSERT review
5. Nếu APPROVED → publish review.posted → Product Service update rating
```

---

### GET /reviews/product/{productId}
Lấy danh sách review của 1 sản phẩm (public, hiển thị ở trang sản phẩm).

**Query Params**
```
page      = 0
size      = 10
rating    = 5           (filter theo số sao, optional)
hasImage  = true        (chỉ review có ảnh, optional)
sort      = newest       (newest | helpful | rating_high | rating_low)
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "reviewId":    "review-uuid-1",
        "userName":    "Nguyen V. A",
        "userAvatar":  "https://storage.shopnow.com/avatars/abc.jpg",
        "rating":      5,
        "comment":     "Áo đẹp, chất vải mát, đúng size.",
        "images": [
          "https://storage.shopnow.com/reviews/img1.jpg"
        ],
        "variantInfo": "Trắng - Size M",
        "isVerifiedPurchase": true,
        "helpfulCount": 12,
        "shopReply": {
          "content": "Cảm ơn bạn đã ủng hộ shop ạ!",
          "repliedAt": "2024-01-21T09:00:00Z"
        },
        "createdAt":   "2024-01-20T10:00:00Z"
      }
    ],
    "page":          0,
    "totalElements": 128,
    "summary": {
      "avgRating":    4.5,
      "totalReviews": 128,
      "distribution": {
        "5": 80,
        "4": 30,
        "3": 10,
        "2": 5,
        "1": 3
      },
      "withImageCount": 45
    }
  }
}
```

**Lưu ý:** `userName` được ẩn bớt (Nguyen V. A) để bảo vệ privacy. `distribution` để vẽ biểu đồ phân bố sao.

---

### GET /reviews/me
Lấy các review user đã viết.

**Header:** `Authorization: Bearer {accessToken}`

**Response 200:** Page danh sách review của user, kèm status (PENDING/APPROVED/REJECTED).

---

### GET /reviews/pending
Lấy các sản phẩm user đã mua nhưng chưa review (nhắc user đánh giá).

**Header:** `Authorization: Bearer {accessToken}`

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "orderId":     "order-uuid-2",
      "productId":   "prod-uuid-5",
      "variantId":   "var-uuid-9",
      "productName": "Quần Jean Nam",
      "thumbnail":   "https://storage.shopnow.com/products/quan-jean/thumb.jpg",
      "variantInfo": "Xanh - Size 32",
      "deliveredAt": "2024-01-18T14:00:00Z"
    }
  ]
}
```

---

### PUT /reviews/{reviewId}
Sửa review của mình (trong vòng 7 ngày sau khi tạo).

**Request Body**
```json
{
  "rating":  4,
  "comment": "Cập nhật: sau 1 tuần dùng thì hơi phai màu."
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã cập nhật đánh giá."
}
```

**Side effect:** Publish `review.posted` lại để Product Service tính lại rating.

---

### DELETE /reviews/{reviewId}
Xóa review của mình.

**Response 200**
```json
{
  "success": true,
  "message": "Đã xóa đánh giá."
}
```

---

### POST /reviews/{reviewId}/helpful
Đánh dấu review hữu ích (like review).

**Header:** `Authorization: Bearer {accessToken}`

**Response 200**
```json
{
  "success": true,
  "data": {
    "helpfulCount": 13,
    "isHelpful":    true
  }
}
```

**Logic:** Toggle — bấm lần nữa thì bỏ. Lưu trong bảng review_helpful (userId + reviewId unique).

---

## 2. SHOP REPLY — Shop trả lời review

---

### POST /admin/reviews/{reviewId}/reply
Shop/Admin trả lời review của khách.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Request Body**
```json
{
  "content": "Cảm ơn bạn đã ủng hộ shop! Rất mong được phục vụ bạn lần sau."
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã trả lời đánh giá."
}
```

---

## 3. MODERATION — Kiểm duyệt

---

### GET /admin/reviews/moderation
Danh sách review cần duyệt (status = PENDING hoặc bị report).

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Query Params**
```
page   = 0
size   = 20
status = PENDING     (PENDING | REPORTED)
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "reviewId":    "review-uuid-2",
        "productName": "Áo Thun Nam",
        "userName":    "user123",
        "rating":      1,
        "comment":     "[nội dung có từ cấm]",
        "flaggedReason":"AUTO_PROFANITY",
        "reportCount": 3,
        "createdAt":   "2024-01-20T11:00:00Z"
      }
    ],
    "page":          0,
    "totalElements": 8
  }
}
```

---

### PATCH /admin/reviews/{reviewId}/approve
Duyệt review (cho hiển thị).

**Response 200**
```json
{
  "success": true,
  "message": "Đã duyệt đánh giá."
}
```

**Side effect:** Publish `review.posted` → Product Service tính rating.

---

### PATCH /admin/reviews/{reviewId}/reject
Từ chối review (ẩn khỏi hiển thị).

**Request Body**
```json
{
  "reason": "Nội dung không phù hợp"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã ẩn đánh giá."
}
```

**Side effect:** Nếu review trước đó đã APPROVED và đã tính vào rating → publish event để trừ lại.

---

## 4. REPORT — User báo cáo review

---

### POST /reviews/{reviewId}/report
User báo cáo review vi phạm.

**Request Body**
```json
{
  "reason": "SPAM"
}
```

**reason values:** `SPAM` / `OFFENSIVE` / `FAKE` / `IRRELEVANT`

**Response 200**
```json
{
  "success": true,
  "message": "Đã gửi báo cáo. Cảm ơn bạn."
}
```

**Logic:** Tăng report_count. Nếu vượt ngưỡng (VD 5 report) → tự động chuyển status = REPORTED để admin xem lại.

---

## 5. INTERNAL

---

### GET /internal/reviews/product/{productId}/summary
Product Service gọi để lấy rating summary (khi cần đồng bộ).

**Response 200**
```json
{
  "productId":    "prod-uuid-1",
  "avgRating":    4.5,
  "totalReviews": 128,
  "distribution": { "5": 80, "4": 30, "3": 10, "2": 5, "1": 3 }
}
```

---

## 6. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `PURCHASE_REQUIRED` | 403 | Chưa mua hàng, không được review |
| `ALREADY_REVIEWED` | 409 | Đã review rồi |
| `REVIEW_NOT_FOUND` | 404 | Review không tồn tại |
| `EDIT_WINDOW_EXPIRED` | 400 | Quá 7 ngày, không sửa được |
| `REVIEW_ACCESS_DENIED` | 403 | Review không thuộc user này |
| `ORDER_NOT_DELIVERED` | 400 | Đơn chưa giao, chưa review được |

---

## 7. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| POST | /reviews | ✅ | USER |
| GET | /reviews/product/{productId} | ❌ | — |
| GET | /reviews/me | ✅ | USER |
| GET | /reviews/pending | ✅ | USER |
| PUT | /reviews/{reviewId} | ✅ | USER |
| DELETE | /reviews/{reviewId} | ✅ | USER |
| POST | /reviews/{reviewId}/helpful | ✅ | USER |
| POST | /reviews/{reviewId}/report | ✅ | USER |
| POST | /admin/reviews/{reviewId}/reply | ✅ | ADMIN |
| GET | /admin/reviews/moderation | ✅ | ADMIN |
| PATCH | /admin/reviews/{reviewId}/approve | ✅ | ADMIN |
| PATCH | /admin/reviews/{reviewId}/reject | ✅ | ADMIN |
| GET | /internal/reviews/product/{productId}/summary | 🔒 Internal | — |

---

---

# PHẦN 2 — DATABASE SCHEMA

---

## Bảng: reviews

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| user_id | UUID | NOT NULL | Người viết |
| user_name | VARCHAR(100) | NOT NULL | Snapshot tên (ẩn bớt khi hiển thị) |
| user_avatar | TEXT | NULLABLE | Snapshot avatar |
| product_id | UUID | NOT NULL | |
| variant_id | UUID | NOT NULL | |
| order_id | UUID | NOT NULL | Đơn hàng đã mua (verified purchase) |
| variant_info | VARCHAR(100) | NULLABLE | Snapshot "Trắng - Size M" |
| rating | SMALLINT | NOT NULL | 1–5 |
| comment | TEXT | NULLABLE | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING / APPROVED / REJECTED |
| flagged_reason | VARCHAR(50) | NULLABLE | Lý do bị flag (AUTO_PROFANITY...) |
| helpful_count | INT | NOT NULL, DEFAULT 0 | |
| report_count | INT | NOT NULL, DEFAULT 0 | |
| is_verified_purchase | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_reviews_product_id_status ON reviews(product_id, status);
CREATE INDEX idx_reviews_user_id ON reviews(user_id);
CREATE UNIQUE INDEX idx_reviews_unique ON reviews(user_id, order_id, variant_id);
CREATE INDEX idx_reviews_status ON reviews(status);
CREATE INDEX idx_reviews_rating ON reviews(rating);
```

**Unique constraint quan trọng:** `(user_id, order_id, variant_id)` — đảm bảo 1 user chỉ review 1 lần cho mỗi variant trong mỗi đơn. Nhưng nếu mua lại ở đơn khác thì được review lần nữa.

---

## Bảng: review_images

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| review_id | UUID | NOT NULL, FK → reviews(id) ON DELETE CASCADE | |
| url | TEXT | NOT NULL | |
| sort_order | INT | NOT NULL, DEFAULT 0 | |

**Index:**
```sql
CREATE INDEX idx_review_images_review_id ON review_images(review_id);
```

---

## Bảng: review_replies

Shop trả lời review.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| review_id | UUID | NOT NULL, UNIQUE, FK → reviews(id) ON DELETE CASCADE | 1 review 1 reply |
| content | TEXT | NOT NULL | |
| replied_by | UUID | NOT NULL | adminId |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_review_replies_review_id ON review_replies(review_id);
```

---

## Bảng: review_helpful

Track user nào đã bấm "hữu ích" cho review nào.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| review_id | UUID | NOT NULL, FK → reviews(id) ON DELETE CASCADE | |
| user_id | UUID | NOT NULL | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_review_helpful_unique ON review_helpful(review_id, user_id);
```

---

## Bảng: review_reports

Track báo cáo vi phạm.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| review_id | UUID | NOT NULL, FK → reviews(id) ON DELETE CASCADE | |
| user_id | UUID | NOT NULL | Người báo cáo |
| reason | VARCHAR(20) | NOT NULL | SPAM / OFFENSIVE / FAKE / IRRELEVANT |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_review_reports_unique ON review_reports(review_id, user_id);
```

---

## Bảng: product_rating_summary

Bảng tổng hợp rating của mỗi sản phẩm — để không phải tính lại mỗi lần query.

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| product_id | UUID | PK | |
| total_reviews | INT | NOT NULL, DEFAULT 0 | |
| sum_rating | BIGINT | NOT NULL, DEFAULT 0 | Tổng điểm (để tính avg nhanh) |
| avg_rating | NUMERIC(3,2) | NOT NULL, DEFAULT 0 | sum_rating / total_reviews |
| count_5 | INT | NOT NULL, DEFAULT 0 | |
| count_4 | INT | NOT NULL, DEFAULT 0 | |
| count_3 | INT | NOT NULL, DEFAULT 0 | |
| count_2 | INT | NOT NULL, DEFAULT 0 | |
| count_1 | INT | NOT NULL, DEFAULT 0 | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Đây là bảng quan trọng — xem phần 3 để hiểu cách tính.**

---

# PHẦN 3 — TÍNH ĐIỂM TRUNG BÌNH (điểm cốt lõi)

---

## Cách SAI — tính lại mỗi lần query

```
Mỗi lần hiển thị sản phẩm:
  SELECT AVG(rating) FROM reviews WHERE product_id = ? AND status = 'APPROVED'

Vấn đề:
  - Sản phẩm có 10.000 review → AVG chậm
  - Trang chủ hiển thị 20 sản phẩm → 20 lần AVG → rất chậm
```

---

## Cách ĐÚNG — bảng tổng hợp + cập nhật incremental

Dùng bảng `product_rating_summary`, cập nhật mỗi khi có review mới thay vì tính lại toàn bộ.

### Khi có review mới (APPROVED)

```sql
-- Cập nhật incremental — chỉ cộng thêm, không tính lại toàn bộ
UPDATE product_rating_summary
SET total_reviews = total_reviews + 1,
    sum_rating    = sum_rating + :newRating,
    avg_rating    = (sum_rating + :newRating)::numeric / (total_reviews + 1),
    count_5       = count_5 + (CASE WHEN :newRating = 5 THEN 1 ELSE 0 END),
    count_4       = count_4 + (CASE WHEN :newRating = 4 THEN 1 ELSE 0 END),
    count_3       = count_3 + (CASE WHEN :newRating = 3 THEN 1 ELSE 0 END),
    count_2       = count_2 + (CASE WHEN :newRating = 2 THEN 1 ELSE 0 END),
    count_1       = count_1 + (CASE WHEN :newRating = 1 THEN 1 ELSE 0 END),
    updated_at    = NOW()
WHERE product_id = :productId;
```

### Khi sửa review (rating thay đổi từ old → new)

```sql
UPDATE product_rating_summary
SET sum_rating = sum_rating - :oldRating + :newRating,
    avg_rating = (sum_rating - :oldRating + :newRating)::numeric / total_reviews,
    -- giảm count sao cũ, tăng count sao mới
    ...
WHERE product_id = :productId;
```

### Khi xóa/reject review

```sql
UPDATE product_rating_summary
SET total_reviews = total_reviews - 1,
    sum_rating    = sum_rating - :rating,
    avg_rating    = CASE
                      WHEN total_reviews - 1 = 0 THEN 0
                      ELSE (sum_rating - :rating)::numeric / (total_reviews - 1)
                    END,
    ...
WHERE product_id = :productId;
```

**Tại sao lưu cả `sum_rating`?**
Để tính avg incremental mà không cần đọc lại tất cả review. `avg = sum / count` — chỉ cần cộng/trừ sum và count.

---

## Đồng bộ sang Product Service

```
Sau khi update product_rating_summary:
  → publish review.posted event
  → Product Service consume → update products.rating + products.review_count
  → Product Service publish product.updated
  → Search Service update ES → rating mới hiển thị trong search

Chuỗi đồng bộ:
Review Service → (review.posted) → Product Service → (product.updated) → Search Service
```

---

## Xử lý concurrency

```
Nhiều user cùng review 1 sản phẩm cùng lúc:
  → UPDATE product_rating_summary có thể bị lost update

Giải pháp:
  - Dùng atomic UPDATE (như SQL trên) — PostgreSQL tự lock row khi UPDATE
  - Hoặc pessimistic lock: SELECT ... FOR UPDATE trước khi tính
  - UPDATE incremental vốn đã atomic ở mức row nên an toàn
```

---

# PHẦN 4 — MODERATION (kiểm duyệt)

---

## Auto-moderation — lọc tự động

```
Khi tạo review, chạy qua các bước filter:
  1. Profanity filter — check danh sách từ cấm
     → Có từ cấm → status = PENDING (chờ admin)
  2. Spam detection — cùng 1 comment lặp nhiều lần
  3. Link detection — comment chứa link lạ → PENDING
  4. Nếu sạch → status = APPROVED tự động
```

---

## Manual moderation — admin duyệt

```
Review vào hàng chờ duyệt khi:
  - Auto-moderation flag (từ cấm, link, spam)
  - report_count vượt ngưỡng (VD 5 report từ user)

Admin xem tại /admin/reviews/moderation:
  - Approve → hiển thị + tính rating
  - Reject → ẩn + trừ rating nếu đã tính
```

---

## Kafka Events

### Publish: review.posted
```json
{
  "eventId":   "uuid-v4",
  "eventType": "review.posted",
  "timestamp": "2024-01-20T10:00:00Z",
  "version":   "1.0",
  "payload": {
    "reviewId":     "review-uuid-1",
    "productId":    "prod-uuid-1",
    "userId":       "user-uuid-1",
    "rating":       5,
    "avgRating":    4.5,
    "totalReviews": 128,
    "action":       "CREATED"
  }
}
```
**action values:** `CREATED` / `UPDATED` / `DELETED`
**Kafka key:** `productId`
**Consumers:**
- Product Service (update products.rating, review_count)
- Recommendation Service (dùng làm tín hiệu behavior)

---

## Redis Keys — Review Service

| Key pattern | Type | TTL | Mục đích |
|-------------|------|-----|---------|
| `processed:event:{eventId}` | String | 24 giờ | Idempotency |
| `review:summary:{productId}` | String (JSON) | 10 phút | Cache rating summary |
| `review:helpful:{reviewId}:{userId}` | String | Không TTL | Track đã like chưa |