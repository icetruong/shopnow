# Fix 5 — categoryId filter bao gồm cả danh mục con

## Vấn đề

Khi user gọi `GET /products?categoryId=A`, code hiện tại chỉ tìm sản phẩm có
`category_id = 'A'` (exact match). Sản phẩm thuộc danh mục con của A sẽ không xuất hiện.

---

## Cần sửa 3 file

---

### Bước 1 — `CategoryRepo.java`

Thêm method sau vào interface:

```java
@Query("SELECT c.id FROM Category c WHERE c.parent.id = :parentId AND c.isActive = true")
List<UUID> findIdsByParentId(@Param("parentId") UUID parentId);
```

> Import cần thêm: `org.springframework.data.jpa.repository.Query`,
> `org.springframework.data.jpa.repository.Param`, `java.util.List`

---

### Bước 2 — `ProductSpecification.java`

Thêm method mới `hasCategoryIds` (nhận List thay vì String):

```java
public static Specification<Product> hasCategoryIds(List<UUID> categoryIds) {
    return (root, query, cb) ->
            categoryIds == null || categoryIds.isEmpty()
                    ? null
                    : root.get("category").get("id").in(categoryIds);
}
```

Sau đó **xóa** method cũ `hasCategoryId(String categoryId)` vì không dùng nữa.

> Import cần thêm: `java.util.List`

---

### Bước 3 — `ProductService.java`, method `getAllProduct()`

Tìm đoạn này (khoảng dòng 60):

```java
Specification<Product> spec = Specification.where(ProductSpecification.isNotDeleted())
        .and(ProductSpecification.hasCategoryId(categoryId))
        .and(ProductSpecification.hasMaxPrice(maxPrice))
        .and(ProductSpecification.hasMinPrice(minPrice))
        .and(ProductSpecification.hasActive(isActive));
```

Thay bằng:

```java
List<UUID> categoryIds = null;
if (categoryId != null) {
    UUID catId = UUID.fromString(categoryId);
    List<UUID> childIds = categoryRepo.findIdsByParentId(catId);
    categoryIds = new ArrayList<>();
    categoryIds.add(catId);
    categoryIds.addAll(childIds);
}

Specification<Product> spec = Specification.where(ProductSpecification.isNotDeleted())
        .and(ProductSpecification.hasCategoryIds(categoryIds))
        .and(ProductSpecification.hasMaxPrice(maxPrice))
        .and(ProductSpecification.hasMinPrice(minPrice))
        .and(ProductSpecification.hasActive(isActive));
```

> `categoryRepo` đã có sẵn trong class rồi, không cần inject thêm.
> `ArrayList` đã import sẵn rồi (`java.util.ArrayList`).

---

## Kết quả sau khi fix

```
GET /products?categoryId=A

categoryId = "A"
  → findIdsByParentId("A") → ["B", "C"]
  → categoryIds = ["A", "B", "C"]
  → WHERE category_id IN ('A', 'B', 'C') ✅
```