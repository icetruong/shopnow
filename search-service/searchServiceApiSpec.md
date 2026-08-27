# Search Service — API Specification & Elasticsearch Index Design

---

## Base URL
```
http://localhost:8089/api/v1
```

> Port `8089` — các service khác đang dùng: user `8081`, product `8082`, inventory `8083`, cart `8084`, order `8085`, payment `8086`, shipping `8087`, notification `8088`.

## Vai trò
Search Service quản lý toàn bộ tìm kiếm sản phẩm qua Elasticsearch. Nó consume Kafka event `product.updated` để đồng bộ dữ liệu từ Product Service vào ES index, và phục vụ full-text search, filter, aggregation cho người dùng.

## Đặc điểm kiến trúc
- **KHÔNG có PostgreSQL** — data sống trong Elasticsearch (PHẦN 2 thay cho DATABASE SCHEMA)
- Consume `product.updated` để index/re-index sản phẩm — **không lead bằng Kafka**, consumer nằm ở PHẦN 4
- Elasticsearch là **read-optimized**, Product Service (PostgreSQL) là **write source of truth**
- Product Service **không** ghi thẳng vào ES — chỉ Search Service upsert vào index

---

## Cấu hình (application.properties)

Đồng bộ convention với order-service / shipping-service / notification-service:

```properties
spring.application.name=search-service
server.port=8089

# JWT (chung public key với các service khác) — các endpoint /search đều public, chỉ cần khi mở rộng
spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:keys/public.pem

# Internal REST
internal.secret-token=${INTERNAL_SECRET_TOKEN}
product.service.url=http://localhost:8082

# Elasticsearch
spring.elasticsearch.uris=http://localhost:9200

# Kafka — GIỐNG order-service: consumer nhận String rồi tự parse bằng ObjectMapper
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=search-service
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
# Producer (không bắt buộc — Search Service hầu như không publish event nào)
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

> **group-id = `search-service`** (không có hậu tố `-group` — `Project_context.md` ghi `search-service-group` là bản cũ). Thống nhất với `groupId = "order-service"`, `group-id = shipping-service`, `group-id = notification-service`.

---

# PHẦN 1 — API ENDPOINTS

---

## 1. SEARCH — Tìm kiếm chính

---

### GET /search
Full-text search + filter + sort + aggregation trong 1 request.

**Query Params**
```
q          = "áo polo nam"     (từ khóa, optional — nếu trống thì browse tất cả)
page       = 0
size       = 20
categoryId = cat-uuid-2        (filter theo danh mục)
minPrice   = 100000
maxPrice   = 500000
colors     = Trắng,Đỏ          (filter nhiều màu, phân cách bằng dấu phẩy)
sizes      = M,L               (filter nhiều size)
minRating  = 4                 (chỉ hiện sản phẩm rating >= 4)
sort       = relevance          (relevance | price_asc | price_desc | newest | bestseller | rating)
```

**Response 200**
```json
{
  "success": true,
  "message": "retrieved successfully",
  "data": {
    "content": [
      {
        "productId":   "prod-uuid-1",
        "name":        "Áo Polo Nam Basic",
        "slug":        "ao-polo-nam-basic",
        "thumbnail":   "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "basePrice":   299000,
        "salePrice":   249000,
        "discountPct": 17,
        "rating":      4.5,
        "reviewCount": 128,
        "soldCount":   340,
        "categoryName":"Áo nam",
        "highlight": {
          "name": ["Áo <em>Polo</em> <em>Nam</em> Basic"]
        }
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 45,
    "totalPages":    3,
    "took":          15,
    "aggregations": {
      "categories": [
        { "categoryId": "cat-uuid-2", "name": "Áo nam",     "count": 32 },
        { "categoryId": "cat-uuid-6", "name": "Áo unisex",  "count": 13 }
      ],
      "priceRanges": [
        { "label": "Dưới 200k",   "from": 0,      "to": 200000, "count": 8 },
        { "label": "200k - 500k", "from": 200000, "to": 500000, "count": 30 },
        { "label": "Trên 500k",   "from": 500000, "to": null,   "count": 7 }
      ],
      "colors": [
        { "value": "Trắng",     "count": 25 },
        { "value": "Đen",       "count": 18 },
        { "value": "Xanh navy", "count": 12 }
      ],
      "sizes": [
        { "value": "S",  "count": 40 },
        { "value": "M",  "count": 45 },
        { "value": "L",  "count": 43 },
        { "value": "XL", "count": 30 }
      ],
      "ratings": [
        { "value": 5, "count": 20 },
        { "value": 4, "count": 18 },
        { "value": 3, "count": 5 }
      ]
    }
  }
}
```

> `content / page / size / totalElements / totalPages` — cùng format phân trang với order-service / notification-service. `took` (thời gian ES xử lý, ms) và `aggregations` là field bổ sung riêng của Search Service (giống `unreadCount` của notification-service).

**Giải thích:**
- `took`: thời gian ES xử lý (ms) — để monitor hiệu năng
- `highlight`: highlight từ khóa khớp trong tên sản phẩm (bôi đậm)
- `aggregations`: dữ liệu để render bộ lọc sidebar với số lượng mỗi filter

---

### GET /search/suggest
Autocomplete — gợi ý khi user đang gõ.

**Query Params**
```
q    = "áo po"
size = 8
```

**Response 200**
```json
{
  "success": true,
  "message": "retrieved successfully",
  "data": {
    "suggestions": [
      { "text": "áo polo nam",       "type": "keyword" },
      { "text": "áo polo nữ",        "type": "keyword" },
      { "text": "áo polo tay dài",   "type": "keyword" }
    ],
    "products": [
      {
        "productId": "prod-uuid-1",
        "name":      "Áo Polo Nam Basic",
        "slug":      "ao-polo-nam-basic",
        "thumbnail": "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
        "salePrice": 249000
      }
    ]
  }
}
```

**Lưu ý:** Dùng ES `completion suggester` hoặc `search_as_you_type` field để autocomplete nhanh.

---

### GET /search/trending
Top từ khóa được tìm kiếm nhiều nhất (hiển thị khi user focus vào ô search chưa gõ gì).

**Response 200**
```json
{
  "success": true,
  "message": "retrieved successfully",
  "data": [
    "áo thun nam",
    "giày sneaker",
    "quần jean",
    "túi xách nữ"
  ]
}
```

**Logic:** Log mỗi query vào Redis sorted set, lấy top theo count. Reset định kỳ (theo tuần).

---

### GET /search/similar/{productId}
Sản phẩm tương tự (dùng ES more_like_this).

**Response 200:** Danh sách sản phẩm tương tự dựa trên tên + category + attributes (cùng shape item với `GET /search`).

---

## 2. INTERNAL — Admin sync

---

### POST /internal/search/reindex
Đồng bộ lại toàn bộ sản phẩm từ Product Service vào ES (dùng khi mới setup hoặc ES bị mất data).

**Header:** `X-Internal-Token: {sharedSecret}`

**Response 202** — chạy async
```json
{
  "success": true,
  "message": "Đã bắt đầu reindex. Quá trình chạy nền.",
  "data": {
    "jobId": "reindex-job-uuid"
  }
}
```

**Flow:**
```
1. Gọi Product Service GET /api/v1/products (phân trang, size 500/lần) để lấy toàn bộ sản phẩm
2. Bulk index vào ES (theo batch 500 sản phẩm/lần)
3. Cập nhật tiến độ vào Redis reindex:progress:{jobId}
```

---

### GET /internal/search/reindex/{jobId}/status
Xem tiến độ reindex.

**Header:** `X-Internal-Token: {sharedSecret}`

**Response 200**
```json
{
  "success": true,
  "data": {
    "jobId":     "reindex-job-uuid",
    "status":    "RUNNING",
    "total":     1500,
    "processed": 900,
    "progress":  60
  }
}
```

---

## 3. ERROR CODES

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `SEARCH_QUERY_TOO_LONG` | 400 | Query quá dài (> 200 ký tự) |
| `INVALID_SORT_OPTION` | 400 | Sort option không hợp lệ |
| `PRODUCT_NOT_FOUND` | 404 | Không tìm thấy sản phẩm (similar) |
| `ELASTICSEARCH_UNAVAILABLE` | 503 | ES — datastore của service — đang không khả dụng |

**Error response format** (giống các service khác):
```json
{
  "success": false,
  "code":    "INVALID_SORT_OPTION",
  "message": "Sort option không hợp lệ."
}
```

---

## 4. TỔNG HỢP ENDPOINTS

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| GET | /search | ❌ | — |
| GET | /search/suggest | ❌ | — |
| GET | /search/trending | ❌ | — |
| GET | /search/similar/{productId} | ❌ | — |
| POST | /internal/search/reindex | 🔒 Internal | — |
| GET | /internal/search/reindex/{jobId}/status | 🔒 Internal | — |

---

---

# PHẦN 2 — ELASTICSEARCH INDEX DESIGN

---

## Index: products

### Settings — Vietnamese analyzer

```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "vietnamese_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": [
            "lowercase",
            "asciifolding"
          ]
        }
      }
    }
  }
}
```

**Giải thích:**
- `lowercase`: chuyển thường (Áo → áo)
- `asciifolding`: bỏ dấu để tìm được cả khi gõ không dấu ("ao polo" tìm ra "Áo Polo")
- Nếu muốn tách từ tiếng Việt chuẩn hơn → cài plugin `analysis-vietnamese` (coccoc-tokenizer) hoặc `analysis-icu`
- Index này do **Search Service sở hữu hoàn toàn** — định nghĩa ở đây là bản chuẩn (Product Service không còn mô tả ES index nữa)

---

### Mappings

```json
{
  "mappings": {
    "properties": {
      "productId":   { "type": "keyword" },
      "name": {
        "type": "text",
        "analyzer": "vietnamese_analyzer",
        "fields": {
          "keyword":    { "type": "keyword" },
          "suggest":    { "type": "search_as_you_type" }
        }
      },
      "slug":        { "type": "keyword" },
      "description": { "type": "text", "analyzer": "vietnamese_analyzer" },
      "categoryId":  { "type": "keyword" },
      "categoryName":{ "type": "keyword" },
      "basePrice":   { "type": "long" },
      "salePrice":   { "type": "long" },
      "rating":      { "type": "float" },
      "reviewCount": { "type": "integer" },
      "soldCount":   { "type": "integer" },
      "colors":      { "type": "keyword" },
      "sizes":       { "type": "keyword" },
      "isActive":    { "type": "boolean" },
      "thumbnail":   { "type": "keyword", "index": false },
      "createdAt":   { "type": "date" },
      "updatedAt":   { "type": "date" }
    }
  }
}
```

**Giải thích chọn type:**
- `keyword`: giá trị chính xác, dùng để filter/aggregate (categoryId, colors, sizes)
- `text`: full-text search, được phân tích qua analyzer (name, description)
- `name` có multi-field: `text` để search, `.keyword` để sort A-Z, `.suggest` để autocomplete
- `thumbnail` set `index: false`: chỉ lưu, không search → tiết kiệm

---

# PHẦN 3 — QUERY DSL CHI TIẾT

---

## Full-text search + filter (bool query)

Khi user search "áo polo nam" + filter category + giá + màu:

```json
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "áo polo nam",
            "fields": ["name^3", "description^1"],
            "type": "best_fields",
            "fuzziness": "AUTO"
          }
        }
      ],
      "filter": [
        { "term":  { "isActive": true } },
        { "term":  { "categoryId": "cat-uuid-2" } },
        { "range": { "salePrice": { "gte": 100000, "lte": 500000 } } },
        { "terms": { "colors": ["Trắng", "Đỏ"] } },
        { "terms": { "sizes": ["M", "L"] } },
        { "range": { "rating": { "gte": 4 } } }
      ]
    }
  },
  "highlight": {
    "fields": {
      "name": {}
    }
  },
  "from": 0,
  "size": 20
}
```

**Giải thích quan trọng:**

`must` vs `filter` — điểm cốt lõi:
- `must` (multi_match): tính **relevance score** — kết quả khớp nhiều thì lên đầu
- `filter`: chỉ lọc **có/không**, KHÔNG tính điểm, và được **cache** → nhanh hơn

`name^3` — boost: điểm khớp ở tên nhân 3 so với description. Sản phẩm có từ khóa trong tên xếp trên sản phẩm chỉ khớp trong mô tả.

`fuzziness: AUTO` — cho phép sai chính tả: gõ "polo" vẫn ra "polo", gõ "poloo" vẫn ra được.

---

## Aggregation — build bộ lọc sidebar

Cùng 1 request, thêm phần `aggs` để lấy số lượng mỗi filter:

```json
{
  "query": { "...": "như trên" },
  "size": 20,
  "aggs": {
    "by_category": {
      "terms": { "field": "categoryId", "size": 20 }
    },
    "by_color": {
      "terms": { "field": "colors", "size": 20 }
    },
    "by_size": {
      "terms": { "field": "sizes", "size": 20 }
    },
    "price_ranges": {
      "range": {
        "field": "salePrice",
        "ranges": [
          { "to": 200000 },
          { "from": 200000, "to": 500000 },
          { "from": 500000 }
        ]
      }
    },
    "by_rating": {
      "terms": { "field": "rating", "size": 5 }
    }
  }
}
```

**Kết quả aggregation** giúp render sidebar như:
```
Danh mục
  ☐ Áo nam (32)
  ☐ Áo unisex (13)
Màu sắc
  ☐ Trắng (25)
  ☐ Đen (18)
Giá
  ☐ Dưới 200k (8)
  ☐ 200k-500k (30)
```

---

## Sort options — mapping sang ES

| sort param | ES sort |
|---|---|
| `relevance` | `_score` desc (mặc định) |
| `price_asc` | `salePrice` asc |
| `price_desc` | `salePrice` desc |
| `newest` | `createdAt` desc |
| `bestseller` | `soldCount` desc |
| `rating` | `rating` desc, `reviewCount` desc |

```json
// Ví dụ sort = price_asc
{
  "query": { "...": "..." },
  "sort": [
    { "salePrice": { "order": "asc" } }
  ]
}

// Khi sort = relevance, KHÔNG cần sort field, ES tự sort theo _score
```

---

## Autocomplete — search_as_you_type

```json
{
  "query": {
    "multi_match": {
      "query": "áo po",
      "type": "bool_prefix",
      "fields": [
        "name.suggest",
        "name.suggest._2gram",
        "name.suggest._3gram"
      ]
    }
  },
  "size": 8,
  "_source": ["productId", "name", "slug", "thumbnail", "salePrice"]
}
```

`_source`: chỉ lấy các field cần cho autocomplete → response nhẹ, nhanh.

---

## More Like This — sản phẩm tương tự

```json
{
  "query": {
    "more_like_this": {
      "fields": ["name", "description", "categoryName"],
      "like": [
        { "_index": "products", "_id": "prod-uuid-1" }
      ],
      "min_term_freq": 1,
      "min_doc_freq": 1,
      "max_query_terms": 12
    }
  },
  "size": 8
}
```

---

# PHẦN 4 — KAFKA CONSUMER & ĐỒNG BỘ DỮ LIỆU

---

## Kafka Event Envelope

Mọi event nhận về là JSON của `KafkaEvent<T>` — **deserialize bằng `ObjectMapper`** (consumer nhận `String`, rồi `objectMapper.readValue(message, new TypeReference<KafkaEvent<ProductUpdatedPayload>>(){})`), **giống hệt order-service / shipping-service / notification-service**:

```json
{
  "eventId":   "uuid-v4",
  "eventType": "product.updated",
  "timestamp": "2024-01-15T10:30:00Z",
  "version":   "1.0",
  "payload":   { ... }
}
```

**Idempotency:** mọi consumer check Redis `processed:event:{eventId}` (giá trị `"1"`, TTL 24h) trước khi xử lý — giống order-service / shipping-service / notification-service.

---

## Consumer: product.updated

**Topic:** `product.updated` · **Publisher:** Product Service · **Kafka key:** `productId` · Envelope `KafkaEvent<ProductUpdatedPayload>`

**`ProductUpdatedPayload`** (theo `ProductServiceApiSpec.md` — Product Service là nguồn phát chuẩn):

| field | kiểu | ghi chú |
|---|---|---|
| productId | string | = document `_id` trong ES |
| name | string | |
| slug | string | |
| description | string | |
| categoryId | string | |
| categoryName | string | |
| basePrice | long | |
| salePrice | long | nullable |
| rating | float | |
| soldCount | int | |
| colors | string[] | gộp từ toàn bộ variant |
| sizes | string[] | gộp từ toàn bộ variant |
| isActive | boolean | |
| isDeleted | boolean | |
| thumbnail | string | |
| updatedAt | date | |

> `reviewCount` / `createdAt` chưa nằm trong payload hiện tại của Product Service. Search Service để `reviewCount = 0` và `createdAt = updatedAt` khi thiếu; nếu cần chính xác, Product Service bổ sung 2 field này vào payload sau (không chặn việc đồng bộ).

**Flow xử lý:**
```
0. IDEMPOTENCY: Redis processed:event:{eventId} tồn tại? → skip
1. objectMapper.readValue → KafkaEvent<ProductUpdatedPayload>
2. Nếu isDeleted = true      → DELETE document {productId} khỏi ES index "products"
3. Nếu isActive = false       → vẫn upsert document, nhưng mọi query search luôn filter isActive = true
                                → sản phẩm ẩn không xuất hiện trong kết quả
4. Bình thường                → map payload → ES document → upsert (document _id = productId)
5. SET Redis processed:event:{eventId} = "1" TTL 24h
```

---

## Luồng đồng bộ Product → Elasticsearch

```
Product Service (PostgreSQL) = source of truth (write)
Elasticsearch                = read replica cho search

Đồng bộ qua Kafka:
  1. Admin tạo/sửa sản phẩm ở Product Service
  2. Product Service UPDATE PostgreSQL
  3. Product Service publish product.updated
  4. Search Service consume → upsert vào ES

→ Eventual consistency: ES có thể chậm hơn DB vài giây, chấp nhận được
```

---

## ES Document format (map từ `payload` của `product.updated`)

```json
{
  "productId":   "prod-uuid-1",
  "name":        "Áo Polo Nam Basic",
  "slug":        "ao-polo-nam-basic",
  "description": "Áo polo chất liệu cotton cao cấp...",
  "categoryId":  "cat-uuid-2",
  "categoryName":"Áo nam",
  "basePrice":   299000,
  "salePrice":   249000,
  "rating":      4.5,
  "reviewCount": 128,
  "soldCount":   340,
  "colors":      ["Trắng", "Xanh navy"],
  "sizes":       ["S", "M", "L"],
  "isActive":    true,
  "thumbnail":   "https://storage.shopnow.com/products/ao-polo/thumb.jpg",
  "createdAt":   "2024-01-10T08:00:00Z",
  "updatedAt":   "2024-01-15T10:00:00Z"
}
```

**Document ID trong ES = productId** → upsert dễ dàng, tránh trùng.

---

## Xử lý các trường hợp

```
product.updated với isDeleted = true
  → DELETE document khỏi ES

product.updated với isActive = false
  → Vẫn index nhưng query luôn filter isActive = true
  → Sản phẩm ẩn không xuất hiện trong search

Bulk reindex (khi ES mất data — POST /internal/search/reindex)
  → Gọi Product Service GET /api/v1/products (paginate 500/lần)
  → ES Bulk API index hàng loạt
```

---

## Bảng tổng hợp Kafka

| Hướng | Topic | Payload |
|---|---|---|
| Consume | `product.updated` | `KafkaEvent<ProductUpdatedPayload>` (productId, name, slug, description, categoryId, categoryName, basePrice, salePrice, rating, soldCount, colors[], sizes[], isActive, isDeleted, thumbnail, updatedAt) |

> Search Service **không publish** event nào — nó là consumer thuần túy đối với hệ thống Kafka.

---

# PHẦN 5 — REDIS & PERFORMANCE

---

## Redis Keys — Search Service

| Key pattern | Value | TTL | Mục đích |
|-------------|-------|-----|---------|
| `processed:event:{eventId}` | `"1"` | 24 giờ | Idempotency Kafka consumer (giống order-service / shipping-service / notification-service) |
| `search:trending` | Sorted Set | 7 ngày | Đếm từ khóa hot |
| `search:cache:{queryHash}` | String (JSON) | 5 phút | Cache kết quả search phổ biến |
| `reindex:progress:{jobId}` | Hash | 1 giờ | Tiến độ reindex |

---

## Performance tips

```
1. Filter thay vì query khi có thể → filter được cache, nhanh hơn
2. Chỉ lấy _source cần thiết → giảm size response
3. Pagination: dùng from/size cho trang đầu, search_after cho deep pagination
   (from quá lớn > 10000 sẽ chậm và tốn RAM)
4. Cache kết quả search phổ biến vào Redis (5 phút)
5. number_of_shards = 1 là đủ cho project học tập (data nhỏ)
6. Dùng bulk API khi index nhiều → nhanh hơn index từng cái nhiều lần
```

---

## Tại sao tách Search Service riêng?

| | Nếu search trong Product Service | Search Service riêng |
|---|---|---|
| Scale | Search nặng làm chậm CRUD | Scale độc lập |
| Công nghệ | PostgreSQL search yếu | Elasticsearch mạnh |
| Coupling | Product phụ thuộc ES | Product không biết ES tồn tại |
| Fail | ES chết → Product chết | ES chết → chỉ search chết, mua hàng vẫn ok |

**Đây là câu trả lời hay khi phỏng vấn:** tách Search Service giúp search fail không ảnh hưởng đến việc đặt hàng — resilience tốt hơn.
