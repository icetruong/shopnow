# ProductSearchService — Changes Summary

## Files Modified

### 1. `Document/ProductDocument.java`
**Bug:** `@Id` was imported from `jakarta.persistence.Id` (JPA) instead of `org.springframework.data.annotation.Id` (Spring Data).  
**Fix:** Changed import to `org.springframework.data.annotation.Id`. Using JPA's `@Id` on an Elasticsearch document breaks mapping entirely.

---

### 2. `Service/ProductSearchService.java` (major rewrite to fix compile errors)

#### Highlight API
- **Removed** `co.elastic.clients.elasticsearch.core.search.Highlight` — `HighlightQuery` in this SDES version only accepts Spring Data's own `Highlight` type, not the ES Java client's.
- **Added** SDES highlight imports:
  - `org.springframework.data.elasticsearch.core.query.highlight.Highlight`
  - `org.springframework.data.elasticsearch.core.query.highlight.HighlightField`
  - `org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters` — separate class from `HighlightParameters` (for per-field settings)
  - `org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters` — for global settings (preTags, postTags)

```java
Highlight highlight = new Highlight(
    HighlightParameters.builder().withPreTags("<em>").withPostTags("</em>").build(),
    List.of(
        new HighlightField("name", HighlightFieldParameters.builder().withNumberOfFragments(0).build()),
        new HighlightField("description", HighlightFieldParameters.builder().withNumberOfFragments(1).withFragmentSize(150).build())
    )
);
```

#### AggregationRange
- **Bug:** `AggregationRange.of(ar -> ar.to("200000"))` — `to()`/`from()` take `Double`, not `String` in this client version.
- **Fix:** `AggregationRange.of(ar -> ar.to(200000.0))`

#### Aggregation result chain
- **Bug:** Removed `.getAggregate()` from the chain — that was wrong. The chain must be:
  - `aggs.get("name")` → `ElasticsearchAggregation` (SDES wrapper)
  - `.aggregation()` → `org.springframework.data.elasticsearch.client.elc.Aggregation` (SDES Aggregation, no sterms/range methods)
  - `.getAggregate()` → `co.elastic.clients.elasticsearch._types.aggregations.Aggregate` (ES client type, has sterms/range)
  - `.sterms()` / `.range()` → specific aggregate result
- **Fix:** Restored `.getAggregate()` before `.sterms()` / `.range()`

#### StringTermsBucket key
- `b.key()` returns `co.elastic.clients.elasticsearch._types.FieldValue`, not `String`.
- **Fix:** Use `b.key().stringValue()` to extract the string value.

#### RangeBucket from/to cast
- `b.from()` and `b.to()` return `@Nullable Double` (boxed), not `double` (primitive).
- **Bug:** `(long) b.from()` — direct cast from `Double` reference to primitive `long` is not allowed in Java.
- **Fix:** Use `.longValue()` with null check:
```java
b.from() != null && b.from() > 0 ? b.from().longValue() : null,
b.to()   != null && b.to()   < Double.MAX_VALUE ? b.to().longValue()   : null,
```

#### Added missing imports
- `co.elastic.clients.elasticsearch._types.aggregations.AggregationRange`
- `com.ice.productservice.Document.ProductDocument`
- `com.ice.productservice.DTO.Response.Search.*`
- `org.springframework.data.domain.PageRequest`
- `org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations`
- `org.springframework.data.elasticsearch.core.SearchHits`
- `org.springframework.data.elasticsearch.core.query.HighlightQuery`
- `java.util.List`

#### Wrong DTO class name
- `SearchProductItemResponse` → `SearchProductResponse` (to match the actual DTO file)

---

### 3. `Service/ProductSyncService.java`
- **Bug:** `syncAll()` was not `@Transactional`. Accessing lazy relations (`productImages`, `productVariants`, `category`) outside a transaction throws `LazyInitializationException`.
- **Fix:** Added `@Transactional(readOnly = true)` to `syncAll()`.

---

### 4. `Service/ProductService.java`
**Bug:** Write operations (create/update/delete/setIsActive) never called `ProductSyncService`, so Elasticsearch was never updated when products changed.

**Fix:** Injected `ProductSyncService` via `@RequiredArgsConstructor` and added sync calls:

| Method | ES sync call |
|--------|-------------|
| `createProduct` | `productSyncService.indexProduct(save)` — after variants created, lazy relations load within `@Transactional` |
| `updateProduct` | `productSyncService.indexProduct(save)` — after attributes updated |
| `setIsActive` | Added `@Transactional`, then `productSyncService.indexProduct(product)` |
| `deleteProduct` | `productSyncService.deleteProduct(product.getId().toString())` — removes doc from ES |

---

### 5. `Controller/ProductController.java`
**Added** search endpoint before `GET /products/{slug}` (literal paths take precedence over path variables in Spring MVC, so no URL conflict):

```
GET /api/v1/products/search
  ?q=keyword
  &page=0
  &size=20
  &categoryId=uuid
  &minPrice=100000
  &maxPrice=500000
  &color=Đen
  &sizeFilter=M
  &sort=price_asc|price_desc|newest|bestseller|rating|relevance
```

Response includes `content[]`, pagination fields, and `aggregations` (categories, priceRanges, colors, sizes).

---

## Known Limitation

`CategoryAggregation.name` currently returns the `categoryId` (UUID) instead of the category name, because Elasticsearch terms aggregation only returns the bucket key. To resolve category names, the frontend should call the category API with the returned IDs, or a sub-aggregation (`top_hits`) can be added later.

---

# Session 2 — Bug fixes, Autocomplete & Kafka

## Files Modified

### 1. `Service/ProductSearchService.java`

**Bug:** NPE trong `toAggregationsResponse()` — `b.from()` và `b.to()` trả về `@Nullable Double`, code cũ unbox trực tiếp gây NPE với bucket đầu (`from = null`) và bucket cuối (`to = null`).

**Fix:**
```java
// Trước
b.from() > 0 ? (long) b.from() : null,
b.to() < Double.MAX_VALUE ? (long) b.to() : null,

// Sau
b.from() != null && b.from() > 0 ? (long) b.from().doubleValue() : null,
b.to() != null && b.to() < Double.MAX_VALUE ? (long) b.to().doubleValue() : null,
```

**Added:** Method `suggest()` cho autocomplete sử dụng `search_as_you_type` field, `multi_match` với `BoolPrefix` type trên `nameSuggest`, `nameSuggest._2gram`, `nameSuggest._3gram`.

**Added:** TODO comment trên class — đánh dấu cần tách sang Search Service.

---

### 2. `Service/ProductService.java`

**Bug:** `updateRating()` cập nhật rating trong PostgreSQL nhưng không sync lên Elasticsearch → search sort theo rating trả kết quả sai.

**Fix:** Thêm `productSyncService.indexProduct(product)` sau `productRepo.save(product)` trong `updateRating()`.

---

### 3. `Repository/ProductRepo.java`

**Bug:** `syncAll()` gọi `productRepo.findAll()` với lazy collections → N+1 queries (1000 sản phẩm = ~3000 queries).

**Fix:** Override `findAll()` với `@EntityGraph(attributePaths = {"productImages", "productVariants", "category"})` để eager load trong 1 query.

---

### 4. `Document/ProductDocument.java`

**Fix:** Đổi `DateFormat.date_hour_minute_second` → `DateFormat.date_optional_time` để tránh mất sub-second precision khi serialize `LocalDateTime`.

**Added:** Field `nameSuggest` với `@Field(type = FieldType.Search_As_You_Type)` cho tính năng autocomplete.

---

### 5. `Service/ProductSyncService.java`

**Added:** `.nameSuggest(product.getName())` vào builder trong `indexProduct()` để populate field autocomplete.

**Added:** TODO comment trên class — đánh dấu cần tách sang Search Service.

---

### 6. `Controller/ProductController.java`

**Added:** Endpoint `GET /api/v1/products/search/suggest?q=&size=` gọi `productSearchService.suggest()`.

**Added:** TODO comment trên 2 search endpoint — đánh dấu cần tách sang Search Service.