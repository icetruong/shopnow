# Product Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8082/api/v1
```

## Auth header (các endpoint cần login)
```
Authorization: Bearer {accessToken}
```

---

# PHẦN 1 — API ENDPOINTS

---

## 1. CATEGORY — Danh mục sản phẩm

---

### GET /categories
Lấy toàn bộ cây danh mục (có phân cấp cha - con).

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "categoryId": "cat-uuid-1",
      "name":       "Thời trang",
      "slug":       "thoi-trang",
      "imageUrl":   "https://storage.shopnow.com/categories/thoi-trang.jpg",
      "children": [
        {
          "categoryId": "cat-uuid-2",
          "name":       "Áo nam",
          "slug":       "ao-nam",
          "imageUrl":   null,
          "children":   []
        },
        {
          "categoryId": "cat-uuid-3",
          "name":       "Quần nam",
          "slug":       "quan-nam",
          "imageUrl":   null,
          "children":   []
        }
      ]
    },
    {
      "categoryId": "cat-uuid-4",
      "name":       "Điện tử",
      "slug":       "dien-tu",
      "imageUrl":   "https://storage.shopnow.com/categories/dien-tu.jpg",
      "children": []
    }
  ]
}
```

---

### POST /admin/categories
Tạo danh mục mới.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Request Body**
```json
{
  "name":       "Giày dép",
  "slug":       "giay-dep",
  "parentId":   null,
  "imageUrl":   "https://storage.shopnow.com/categories/giay-dep.jpg",
  "sortOrder":  3
}
```

**Validation**
- `name`: không trống, 2–100 ký tự
- `slug`: không trống, chỉ chứa chữ thường + dấu gạch ngang, unique
- `parentId`: nullable, phải tồn tại nếu có, tối đa 2 cấp (cha → con)

**Response 201**
```json
{
  "success": true,
  "data": {
    "categoryId": "cat-uuid-5",
    "name":       "Giày dép",
    "slug":       "giay-dep"
  }
}
```

---

### PUT /admin/categories/{categoryId}
Cập nhật danh mục.

**Request Body:** Giống POST, tất cả optional.

**Response 200**
```json
{
  "success": true,
  "message": "Cập nhật danh mục thành công."
}
```

---

### DELETE /admin/categories/{categoryId}
Xóa danh mục. Không xóa được nếu còn sản phẩm thuộc danh mục này.

**Response 200**
```json
{
  "success": true,
  "message": "Đã xóa danh mục."
}
```

**Response 409 — Còn sản phẩm**
```json
{
  "success": false,
  "code":    "CATEGORY_HAS_PRODUCTS",
  "message": "Không thể xóa danh mục đang có sản phẩm."
}
```

**Response 409 — Còn danh mục con**
```json
{
  "success": false,
  "code":    "CATEGORY_HAS_CHILDREN",
  "message": "Không thể xóa danh mục đang có danh mục con."
}
```

---

## 2. PRODUCT — Sản phẩm

---

### GET /products
Lấy danh sách sản phẩm có phân trang + filter + sort. Dữ liệu lấy từ **Redis cache** trước, nếu miss thì query DB.

**Query Params**
```
page        = 0
size        = 20
sort        = createdAt     (createdAt | price | name | soldCount | rating)
direction   = DESC          (ASC | DESC)
categoryId  = cat-uuid-1    (filter theo danh mục, bao gồm cả danh mục con)
minPrice    = 100000
maxPrice    = 500000
isActive    = true
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "productId":    "prod-uuid-1",
        "name":         "Áo Polo Nam Basic",
        "slug":         "ao-polo-nam-basic",
        "thumbnail":    "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "basePrice":    299000,
        "salePrice":    249000,
        "discountPct":  17,
        "rating":       4.5,
        "reviewCount":  128,
        "soldCount":    340,
        "categoryId":   "cat-uuid-2",
        "categoryName": "Áo nam",
        "isActive":     true
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 85,
    "totalPages":    5,
    "isLast":        false
  }
}
```

**Cache strategy:** Cache key `products:list:{hash(queryParams)}`, TTL 5 phút. Khi admin tạo/sửa/xóa sản phẩm → invalidate cache theo pattern `products:list:*`.

---

### GET /products/{slug}
Lấy chi tiết sản phẩm kèm toàn bộ variant và ảnh. Cache theo slug, TTL 10 phút.

**Response 200**
```json
{
  "success": true,
  "data": {
    "productId":   "prod-uuid-1",
    "name":        "Áo Polo Nam Basic",
    "slug":        "ao-polo-nam-basic",
    "description": "Áo polo chất liệu cotton cao cấp...",
    "basePrice":   299000,
    "salePrice":   249000,
    "discountPct": 17,
    "rating":      4.5,
    "reviewCount": 128,
    "soldCount":   340,
    "categoryId":  "cat-uuid-2",
    "categoryName":"Áo nam",
    "isActive":    true,
    "images": [
      {
        "imageId":  "img-uuid-1",
        "url":      "https://storage.shopnow.com/products/ao-polo/main.jpg",
        "altText":  "Áo Polo Nam Basic màu trắng",
        "sortOrder": 1,
        "isPrimary": true
      },
      {
        "imageId":  "img-uuid-2",
        "url":      "https://storage.shopnow.com/products/ao-polo/2.jpg",
        "altText":  "Áo Polo Nam Basic màu xanh",
        "sortOrder": 2,
        "isPrimary": false
      }
    ],
    "attributes": [
      { "name": "Chất liệu", "value": "Cotton 100%" },
      { "name": "Xuất xứ",  "value": "Việt Nam" }
    ],
    "variants": [
      {
        "variantId": "var-uuid-1",
        "sku":       "POLO-WHITE-S",
        "color":     "Trắng",
        "size":      "S",
        "price":     249000,
        "stockQty":  50,
        "imageUrl":  "https://storage.shopnow.com/products/ao-polo/white.jpg"
      },
      {
        "variantId": "var-uuid-2",
        "sku":       "POLO-WHITE-M",
        "color":     "Trắng",
        "size":      "M",
        "price":     249000,
        "stockQty":  30,
        "imageUrl":  "https://storage.shopnow.com/products/ao-polo/white.jpg"
      },
      {
        "variantId": "var-uuid-3",
        "sku":       "POLO-BLUE-S",
        "color":     "Xanh navy",
        "size":      "S",
        "price":     249000,
        "stockQty":  0,
        "imageUrl":  "https://storage.shopnow.com/products/ao-polo/blue.jpg"
      }
    ],
    "createdAt": "2024-01-10T08:00:00Z",
    "updatedAt": "2024-01-15T10:00:00Z"
  }
}
```

---

### POST /admin/products
Tạo sản phẩm mới (chưa có ảnh, ảnh upload riêng).

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Request Body**
```json
{
  "name":        "Áo Polo Nam Basic",
  "slug":        "ao-polo-nam-basic",
  "description": "Áo polo chất liệu cotton cao cấp...",
  "categoryId":  "cat-uuid-2",
  "basePrice":   299000,
  "salePrice":   249000,
  "isActive":    false,
  "attributes": [
    { "name": "Chất liệu", "value": "Cotton 100%" },
    { "name": "Xuất xứ",  "value": "Việt Nam" }
  ],
  "variants": [
    {
      "sku":      "POLO-WHITE-S",
      "color":    "Trắng",
      "size":     "S",
      "price":    249000,
      "stockQty": 50
    },
    {
      "sku":      "POLO-WHITE-M",
      "color":    "Trắng",
      "size":     "M",
      "price":    249000,
      "stockQty": 30
    }
  ]
}
```

**Validation**
- `name`: không trống, 2–255 ký tự
- `slug`: unique, chỉ chứa chữ thường + dấu gạch ngang
- `categoryId`: phải tồn tại
- `basePrice`: > 0
- `salePrice`: nullable, nếu có phải <= basePrice
- `variants`: tối thiểu 1 variant, `sku` unique toàn hệ thống

**Response 201**
```json
{
  "success": true,
  "data": {
    "productId": "prod-uuid-1",
    "slug":      "ao-polo-nam-basic"
  }
}
```

**Side effect:** Publish Kafka event `product.updated` → Search Service index vào Elasticsearch.

---

### PUT /admin/products/{productId}
Cập nhật thông tin sản phẩm (không bao gồm ảnh, không bao gồm variant).

**Request Body:** Giống POST nhưng không có `variants`, tất cả field optional.

**Response 200**
```json
{
  "success": true,
  "message": "Cập nhật sản phẩm thành công."
}
```

**Side effect:** Invalidate Redis cache, publish `product.updated`.

---

### PATCH /admin/products/{productId}/status
Bật/tắt hiển thị sản phẩm.

**Request Body**
```json
{
  "isActive": true
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã cập nhật trạng thái sản phẩm."
}
```

---

### DELETE /admin/products/{productId}
Xóa mềm sản phẩm (soft delete — set `isActive = false`, không xóa khỏi DB).

**Response 200**
```json
{
  "success": true,
  "message": "Đã xóa sản phẩm."
}
```

---

## 3. VARIANT — Biến thể sản phẩm

---

### POST /admin/products/{productId}/variants
Thêm variant mới vào sản phẩm đã có.

**Request Body**
```json
{
  "sku":      "POLO-RED-L",
  "color":    "Đỏ",
  "size":     "L",
  "price":    249000,
  "stockQty": 20,
  "imageUrl": "https://storage.shopnow.com/products/ao-polo/red.jpg"
}
```

**Validation**
- `sku`: unique toàn hệ thống
- `price`: > 0
- `stockQty`: >= 0

**Response 201**
```json
{
  "success": true,
  "data": {
    "variantId": "var-uuid-4"
  }
}
```

---

### PUT /admin/products/{productId}/variants/{variantId}
Cập nhật thông tin variant (giá, ảnh). Không cho phép đổi SKU.

**Request Body**
```json
{
  "price":    259000,
  "imageUrl": "https://storage.shopnow.com/products/ao-polo/red-new.jpg"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Cập nhật variant thành công."
}
```

---

### DELETE /admin/products/{productId}/variants/{variantId}
Xóa variant. Không xóa được nếu variant đang có trong đơn hàng đang xử lý.

**Response 200**
```json
{
  "success": true,
  "message": "Đã xóa variant."
}
```

**Response 409**
```json
{
  "success": false,
  "code":    "VARIANT_IN_ACTIVE_ORDER",
  "message": "Không thể xóa variant đang có trong đơn hàng chưa hoàn thành."
}
```

---

## 4. IMAGE — Ảnh sản phẩm

---

### POST /admin/products/{productId}/images
Upload ảnh sản phẩm lên MinIO/S3. Hỗ trợ upload nhiều ảnh cùng lúc.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Request:** `multipart/form-data`
```
files:     [binary images] (tối đa 10 file, mỗi file tối đa 5MB)
altTexts:  ["Ảnh mặt trước", "Ảnh mặt sau"]   (optional, JSON array string)
```

**Validation:** JPG / PNG / WEBP. Tối đa 10 ảnh / sản phẩm.

**Response 201**
```json
{
  "success": true,
  "data": [
    {
      "imageId":   "img-uuid-3",
      "url":       "https://storage.shopnow.com/products/prod-uuid-1/image-3.jpg",
      "altText":   "Ảnh mặt trước",
      "sortOrder": 3,
      "isPrimary": false
    }
  ]
}
```

**Upload flow:**
```
Client → POST /images (multipart)
  → Validate file type & size
  → Upload lên MinIO/S3 với key: products/{productId}/{uuid}.{ext}
  → Lưu metadata vào DB (bảng product_images)
  → Trả về public URL
```

---

### PATCH /admin/products/{productId}/images/{imageId}/primary
Đặt ảnh này làm ảnh đại diện (thumbnail) của sản phẩm.

**Response 200**
```json
{
  "success": true,
  "message": "Đã đặt làm ảnh đại diện."
}
```

---

### PATCH /admin/products/{productId}/images/sort
Sắp xếp lại thứ tự ảnh.

**Request Body**
```json
{
  "orders": [
    { "imageId": "img-uuid-2", "sortOrder": 1 },
    { "imageId": "img-uuid-1", "sortOrder": 2 },
    { "imageId": "img-uuid-3", "sortOrder": 3 }
  ]
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã cập nhật thứ tự ảnh."
}
```

---

### DELETE /admin/products/{productId}/images/{imageId}
Xóa ảnh — xóa cả file trên MinIO/S3 và record trong DB.

**Response 200**
```json
{
  "success": true,
  "message": "Đã xóa ảnh."
}
```

---

## 5. SEARCH — Tìm kiếm Elasticsearch

---

### GET /products/search
Tìm kiếm sản phẩm full-text qua Elasticsearch.

**Query Params**
```
q           = "áo polo nam"     (từ khóa tìm kiếm — bắt buộc)
page        = 0
size        = 20
categoryId  = cat-uuid-2        (filter)
minPrice    = 100000
maxPrice    = 500000
color       = "Trắng"           (filter theo màu sắc trong variant)
size_filter = "M"               (filter theo size trong variant, dùng size_filter để không trùng query param size)
sort        = relevance          (relevance | price_asc | price_desc | newest | bestseller | rating)
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "productId":   "prod-uuid-1",
        "name":        "Áo Polo Nam Basic",
        "slug":        "ao-polo-nam-basic",
        "thumbnail":   "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "basePrice":   299000,
        "salePrice":   249000,
        "rating":      4.5,
        "soldCount":   340,
        "highlight": {
          "name":        ["Áo <em>Polo</em> <em>Nam</em> Basic"],
          "description": ["...chất liệu phù hợp cho <em>nam</em> giới..."]
        }
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 12,
    "totalPages":    1,
    "isLast":        true,
    "aggregations": {
      "categories": [
        { "categoryId": "cat-uuid-2", "name": "Áo nam", "count": 10 },
        { "categoryId": "cat-uuid-6", "name": "Áo unisex", "count": 2 }
      ],
      "priceRanges": [
        { "from": 0,      "to": 200000, "count": 2 },
        { "from": 200000, "to": 500000, "count": 8 },
        { "from": 500000, "to": null,   "count": 2 }
      ],
      "colors": [
        { "value": "Trắng",    "count": 6 },
        { "value": "Xanh navy","count": 4 },
        { "value": "Đỏ",      "count": 2 }
      ],
      "sizes": ["S", "M", "L", "XL"]
    }
  }
}
```

**Lưu ý:** `aggregations` dùng để render bộ lọc bên sidebar — trả về count của từng giá trị filter.

---

### GET /products/search/suggest
Gợi ý tìm kiếm (autocomplete) khi user đang gõ.

**Query Params**
```
q    = "áo po"    (từ khóa đang gõ dở)
size = 5          (số gợi ý, default 5, max 10)
```

**Response 200**
```json
{
  "success": true,
  "data": [
    "Áo Polo Nam Basic",
    "Áo Polo Nam Cao Cấp",
    "Áo Polo Unisex"
  ]
}
```

---

## 6. INTERNAL — Dành cho các service khác

---

### GET /internal/products/{productId}
Order Service gọi để snapshot giá + thông tin sản phẩm lúc đặt hàng.

**Header:** `X-Internal-Token: {sharedSecret}`

**Response 200**
```json
{
  "productId":   "prod-uuid-1",
  "name":        "Áo Polo Nam Basic",
  "isActive":    true,
  "categoryId":  "cat-uuid-2"
}
```

---

### GET /internal/products/{productId}/variants/{variantId}
Lấy thông tin variant + giá để tạo order item.

**Response 200**
```json
{
  "variantId": "var-uuid-1",
  "productId": "prod-uuid-1",
  "sku":       "POLO-WHITE-S",
  "color":     "Trắng",
  "size":      "S",
  "price":     249000,
  "isActive":  true
}
```

---

### POST /internal/products/rating
Review Service gọi sau khi có review mới để cập nhật điểm trung bình.

**Request Body**
```json
{
  "productId":    "prod-uuid-1",
  "newRating":    5,
  "totalReviews": 129,
  "avgRating":    4.51
}
```

**Response 200**
```json
{
  "success": true
}
```

---

## 7. ERROR CODES đặc thù của Product Service

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `NOT_FOUND` | 404 | Resource không tồn tại (sản phẩm, danh mục, variant, ảnh) |
| `ALREADY_EXISTS` | 409 | Slug hoặc SKU đã tồn tại |
| `CATEGORY_HAS_PRODUCTS` | 409 | Xóa danh mục đang có sản phẩm |
| `CATEGORY_HAS_CHILDREN` | 409 | Xóa danh mục đang có danh mục con |
| `TOO_MANY_IMAGES` | 400 | Quá 10 ảnh / sản phẩm |
| `INVALID_FILE_TYPE` | 400 | File không phải JPG/PNG/WEBP |
| `FILE_TOO_LARGE` | 400 | File quá 5MB |
| `VARIANT_IN_ACTIVE_ORDER` | 409 | Variant đang trong đơn hàng |
| `INVALID_REQUEST` | 400 | Dữ liệu request không hợp lệ (validation fail) |

---

## 8. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| GET | /categories | ❌ | — |
| POST | /admin/categories | ✅ | ADMIN |
| PUT | /admin/categories/{id} | ✅ | ADMIN |
| DELETE | /admin/categories/{id} | ✅ | ADMIN |
| GET | /products | ❌ | — |
| GET | /products/{slug} | ❌ | — |
| GET | /products/search | ❌ | — |
| GET | /products/search/suggest | ❌ | — |
| POST | /admin/products | ✅ | ADMIN |
| PUT | /admin/products/{id} | ✅ | ADMIN |
| PATCH | /admin/products/{id}/status | ✅ | ADMIN |
| DELETE | /admin/products/{id} | ✅ | ADMIN |
| POST | /admin/products/{id}/variants | ✅ | ADMIN |
| PUT | /admin/products/{id}/variants/{vid} | ✅ | ADMIN |
| DELETE | /admin/products/{id}/variants/{vid} | ✅ | ADMIN |
| POST | /admin/products/{id}/images | ✅ | ADMIN |
| PATCH | /admin/products/{id}/images/{iid}/primary | ✅ | ADMIN |
| PATCH | /admin/products/{id}/images/sort | ✅ | ADMIN |
| DELETE | /admin/products/{id}/images/{iid} | ✅ | ADMIN |
| GET | /internal/products/{id} | 🔒 Internal | — |
| GET | /internal/products/{id}/variants/{vid} | 🔒 Internal | — |
| POST | /internal/products/rating | 🔒 Internal | — |

---

---

# PHẦN 2 — DATABASE SCHEMA

---

## Bảng: categories

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| name | VARCHAR(100) | NOT NULL | |
| slug | VARCHAR(120) | NOT NULL, UNIQUE | |
| parent_id | UUID | NULLABLE, FK → categories(id) | NULL = danh mục gốc |
| image_url | TEXT | NULLABLE | |
| sort_order | INT | NOT NULL, DEFAULT 0 | |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);
```

---

## Bảng: products

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| name | VARCHAR(255) | NOT NULL | |
| slug | VARCHAR(300) | NOT NULL, UNIQUE | |
| description | TEXT | NULLABLE | |
| category_id | UUID | NOT NULL, FK → categories(id) | |
| base_price | BIGINT | NOT NULL | Giá gốc (VND, lưu nguyên — không dùng DECIMAL tránh lỗi float) |
| sale_price | BIGINT | NULLABLE | Giá khuyến mãi |
| rating | NUMERIC(3,2) | NOT NULL, DEFAULT 0 | 0.00 → 5.00 |
| review_count | INT | NOT NULL, DEFAULT 0 | |
| sold_count | INT | NOT NULL, DEFAULT 0 | |
| is_active | BOOLEAN | NOT NULL, DEFAULT FALSE | False khi mới tạo, admin bật thủ công |
| is_deleted | BOOLEAN | NOT NULL, DEFAULT FALSE | Soft delete |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_is_active_is_deleted ON products(is_active, is_deleted);
CREATE INDEX idx_products_sale_price ON products(sale_price);
CREATE INDEX idx_products_rating ON products(rating DESC);
CREATE INDEX idx_products_sold_count ON products(sold_count DESC);
```

---

## Bảng: product_attributes

Lưu các thông số mô tả sản phẩm dạng key-value (Chất liệu, Xuất xứ...).

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| product_id | UUID | NOT NULL, FK → products(id) ON DELETE CASCADE | |
| name | VARCHAR(100) | NOT NULL | VD: "Chất liệu" |
| value | VARCHAR(255) | NOT NULL | VD: "Cotton 100%" |
| sort_order | INT | NOT NULL, DEFAULT 0 | |

**Index:**
```sql
CREATE INDEX idx_product_attributes_product_id ON product_attributes(product_id);
```

---

## Bảng: product_variants

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| product_id | UUID | NOT NULL, FK → products(id) ON DELETE CASCADE | |
| sku | VARCHAR(100) | NOT NULL, UNIQUE | Mã phân loại hàng, unique toàn hệ thống |
| color | VARCHAR(50) | NULLABLE | |
| size | VARCHAR(20) | NULLABLE | S / M / L / XL / XXL hoặc 38 / 39 / 40... |
| price | BIGINT | NOT NULL | Giá của riêng variant này |
| image_url | TEXT | NULLABLE | Ảnh riêng của variant (ảnh màu) |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_variants_sku ON product_variants(sku);
CREATE INDEX idx_variants_product_id ON product_variants(product_id);
CREATE INDEX idx_variants_color ON product_variants(color);
CREATE INDEX idx_variants_size ON product_variants(size);
```

**Lưu ý:** `stock_qty` KHÔNG lưu ở bảng này — tồn kho được quản lý bởi **Inventory Service** riêng. Product Service chỉ expose thông tin product + variant, Inventory Service mới có số lượng thực tế.

---

## Bảng: product_images

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| product_id | UUID | NOT NULL, FK → products(id) ON DELETE CASCADE | |
| url | TEXT | NOT NULL | Public URL trên MinIO/S3 |
| storage_key | VARCHAR(500) | NOT NULL | Key nội bộ trên MinIO/S3 (dùng để xóa file) |
| alt_text | VARCHAR(255) | NULLABLE | |
| sort_order | INT | NOT NULL, DEFAULT 0 | |
| is_primary | BOOLEAN | NOT NULL, DEFAULT FALSE | Ảnh đại diện (thumbnail) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_product_images_product_id ON product_images(product_id);
```

**Constraint:** Đảm bảo chỉ có 1 ảnh primary mỗi sản phẩm — xử lý trong Service (update is_primary = false hết, rồi set cái mới = true).

---

## Redis Keys — Product Service

| Key pattern | Value | TTL | Mục đích |
|-------------|-------|-----|---------|
| `product:detail:{slug}` | JSON ProductDetailDTO | 10 phút | Cache chi tiết sản phẩm |
| `products:list:{hash}` | JSON Page<ProductSummaryDTO> | 5 phút | Cache danh sách sản phẩm |
| `categories:tree` | JSON toàn bộ cây danh mục | 30 phút | Cache ít thay đổi |

**Invalidation:**
- Khi tạo / sửa / xóa sản phẩm → xóa `product:detail:{slug}` + xóa tất cả `products:list:*`
- Khi sửa danh mục → xóa `categories:tree`

---

## Elasticsearch Index — products

```json
{
  "mappings": {
    "properties": {
      "productId":   { "type": "keyword" },
      "name":        {
        "type": "text",
        "analyzer": "vi_analyzer",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "slug":        { "type": "keyword" },
      "description": { "type": "text", "analyzer": "vi_analyzer" },
      "categoryId":  { "type": "keyword" },
      "categoryName":{ "type": "keyword" },
      "basePrice":   { "type": "long" },
      "salePrice":   { "type": "long" },
      "rating":      { "type": "float" },
      "soldCount":   { "type": "integer" },
      "reviewCount": { "type": "integer" },
      "isActive":    { "type": "boolean" },
      "isDeleted":   { "type": "boolean" },
      "colors":      { "type": "keyword" },
      "sizes":       { "type": "keyword" },
      "thumbnail":   { "type": "keyword", "index": false },
      "createdAt":   { "type": "date" },
      "updatedAt":   { "type": "date" }
    }
  }
}
```

**Lưu ý:**
- `colors` và `sizes` là mảng keyword — lấy từ toàn bộ variant của sản phẩm, dùng để filter.
- Khi nào sync vào ES: **Search Service** consume Kafka event `product.updated` rồi tự upsert. Product Service không ghi thẳng vào ES.
- `vi_analyzer`: cần cài plugin `analysis-icu` để tìm kiếm tiếng Việt đúng dấu.

---

## Kafka Events publish từ Product Service

### product.updated
Publish khi tạo mới, cập nhật, hoặc thay đổi trạng thái sản phẩm.

```json
{
  "eventId":   "uuid-v4",
  "eventType": "product.updated",
  "timestamp": "2024-01-15T10:30:00Z",
  "version":   "1.0",
  "payload": {
    "productId":    "prod-uuid-1",
    "name":         "Áo Polo Nam Basic",
    "slug":         "ao-polo-nam-basic",
    "description":  "Áo polo chất liệu cotton cao cấp...",
    "categoryId":   "cat-uuid-2",
    "categoryName": "Áo nam",
    "basePrice":    299000,
    "salePrice":    249000,
    "rating":       4.5,
    "soldCount":    340,
    "thumbnail":    "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
    "isActive":     true,
    "isDeleted":    false,
    "colors":       ["Trắng", "Xanh navy"],
    "sizes":        ["S", "M", "L"],
    "updatedAt":    "2024-01-15T10:00:00Z"
  }
}
```

**Kafka key:** `productId`
**Consumer:** Search Service (re-index Elasticsearch)

---

## MinIO / S3 — Cấu trúc lưu file

```
bucket: shopnow-products
  └── {productId}/
        ├── {uuid}.jpg        ← ảnh sản phẩm
        ├── {uuid}.jpg
        └── ...

bucket: shopnow-categories
  └── {categoryId}.jpg        ← ảnh danh mục
```

**URL public:** `https://storage.shopnow.com/shopnow-products/{productId}/{uuid}.jpg`

**Upload flow chi tiết:**
```
1. Client gửi file lên POST /admin/products/{id}/images
2. Service validate type (whitelist: jpg, png, webp) + size (max 5MB)
3. Generate storage key: products/{productId}/{UUID}.{ext}
4. Upload lên MinIO/S3 bằng AWS SDK hoặc MinIO Java SDK
5. Lưu record vào bảng product_images với url + storage_key
6. Trả về public URL cho client
7. Khi xóa ảnh: dùng storage_key để xóa file trên MinIO/S3, sau đó xóa record DB
```