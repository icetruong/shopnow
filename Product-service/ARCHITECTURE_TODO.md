# Architecture TODO — Xử lý sau khi có đủ services

Các vấn đề dưới đây **không phải bug**, mà là thiết kế chưa hoàn chỉnh vì các service liên quan chưa được xây dựng. Khi hệ thống có đủ microservices thì quay lại xử lý.

---

## 1. Product Service đang ghi thẳng vào Elasticsearch ❌

**Hiện tại:**
```
POST /admin/products
  → ProductService.createProduct()
  → productSyncService.indexProduct(save)   ← GHI THẲNG VÀO ES
  → kafkaProducerService.publish(save)
```

**Đúng theo kiến trúc microservice:**
```
POST /admin/products
  → ProductService.createProduct()
  → kafkaProducerService.publish(save)      ← chỉ publish event

[Search Service - service riêng biệt]
  → consume Kafka topic "product.updated"
  → tự index vào Elasticsearch
```

**Tại sao phải tách?**
- Product Service chỉ nên quản lý dữ liệu sản phẩm. Việc search/index là trách nhiệm của Search Service.
- Nếu Elasticsearch chết, Product Service không nên bị ảnh hưởng.
- Scale độc lập: Search Service có thể scale riêng khi tải search tăng cao.

**Cần làm khi có Search Service:**
1. Xóa `ProductSyncService.java` khỏi Product Service.
2. Xóa `ProductSearchService.java` khỏi Product Service (move sang Search Service).
3. Xóa dependency `spring-data-elasticsearch` khỏi `pom.xml` của Product Service.
4. Search Service tạo Kafka consumer lắng nghe topic `product.updated` và tự index.
5. Endpoint `GET /products/search` và `GET /products/search/suggest` chuyển sang Search Service (hoặc API Gateway route đến Search Service).

---

## 2. `stockQty` luôn trả về 0 ❌

**Hiện tại:** `ProductService.java:270`
```java
0, // chưa lấy được từ Inventory Service
```

**Đúng theo kiến trúc:**
- `stock_qty` KHÔNG lưu trong Product Service (đúng rồi, không có cột này trong DB).
- Khi trả về variant detail, Product Service phải gọi **Inventory Service** để lấy số lượng tồn kho thực tế.

**Cần làm khi có Inventory Service:**
1. Thêm Feign Client (hoặc WebClient) để gọi Inventory Service.
2. Trong `ProductService.toVariantProductDetailResponse()`, thay `0` bằng lời gọi:
   ```java
   inventoryClient.getStock(productVariant.getId())
   ```
3. Xử lý fallback nếu Inventory Service không phản hồi (trả về `null` hoặc `-1` thay vì crash).

**Lưu ý:** API response vẫn giữ field `stockQty`, chỉ là value sẽ thực thay vì `0`.

---

## 3. Elasticsearch chưa dùng `vi_analyzer` (tiếng Việt) ❌

**Hiện tại:** `ProductDocument.java`
```java
@Field(type = FieldType.Text, analyzer = "standard")
private String name;
```

**Vấn đề:** Analyzer `standard` không hiểu dấu tiếng Việt.
- Search `"ao polo"` sẽ **không match** `"áo polo"`.
- Search `"quan jean"` sẽ **không match** `"quần jean"`.

**Cần làm:**
1. Cài plugin `analysis-icu` trên Elasticsearch cluster.
2. Tạo index mapping với custom analyzer:
   ```json
   {
     "settings": {
       "analysis": {
         "analyzer": {
           "vi_analyzer": {
             "type": "custom",
             "tokenizer": "icu_tokenizer",
             "filter": ["icu_folding"]
           }
         }
       }
     }
   }
   ```
3. Sửa `ProductDocument.java`:
   ```java
   @Field(type = FieldType.Text, analyzer = "vi_analyzer")
   private String name;

   @Field(type = FieldType.Text, analyzer = "vi_analyzer")
   private String description;
   ```
4. Re-index toàn bộ data (`productSyncService.syncAll()`).

**Lưu ý:** Việc này phụ thuộc vào item #1 (tách Search Service). Nếu tách rồi thì cấu hình analyzer trong Search Service.

---

## 4. `deleteVariant` — check đơn hàng đang xử lý hardcode `false` ❌

**Hiện tại:** `ProductVariantService.java:82`
```java
if (false) // ← HARDCODE, không bao giờ check thật
    throw new VariantInActiveOrderException("...");
```

**Đúng theo kiến trúc:**
- Trước khi xóa variant, phải gọi **Order Service** để kiểm tra xem variant này có đang nằm trong đơn hàng nào trạng thái `PENDING` / `PROCESSING` không.

**Cần làm khi có Order Service:**
1. Thêm Feign Client để gọi Order Service.
2. Thay `if(false)` bằng:
   ```java
   boolean inActiveOrder = orderClient.isVariantInActiveOrder(id);
   if (inActiveOrder)
       throw new VariantInActiveOrderException("...");
   ```

---

## Thứ tự ưu tiên xây dựng

```
1. Inventory Service  →  fix stockQty
2. Order Service      →  fix deleteVariant check
3. Search Service     →  tách ES indexing, thêm vi_analyzer
```