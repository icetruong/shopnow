# TODO — Inventory Service

## GET /admin/stock — Cần gọi Product Service

**Endpoint:** `GET /api/v1/admin/stock?productId=...&variantId=...&status=...&page=...&size=...`

**Vấn đề:** Bảng `inventories` không lưu `productId`, `productName`, `color`, `size` — những field này nằm ở Product Service.

**Việc cần làm:**

1. Thêm Feign Client (hoặc WebClient) để gọi Product Service
   - Nếu request có `productId` → gọi Product Service lấy danh sách `variantId` thuộc product đó
   - Sau khi query xong inventory → gọi Product Service để enrich `productName`, `color`, `size` cho từng variant trong response

2. Endpoint Product Service cần gọi (hỏi team Product hoặc xem spec Product Service):
   - `GET /internal/variants?productId={productId}` → trả List variantId
   - `GET /internal/variants/batch` với body là List variantId → trả detail (productName, color, size)

3. Xử lý fallback: nếu Product Service không respond → trả inventory data nhưng để trống productName/color/size, không để cả request fail

**File cần tạo:**
- `Client/ProductServiceClient.java` — Feign Client
- `Config/FeignConfig.java` — config timeout, header X-Internal-Token
- `DTO/Response/Product/VariantDetailResponse.java` — map response từ Product Service