Đây là hướng dẫn đầy đủ implement Elasticsearch search:

  ---
Bước 1 — pom.xml — Thêm dependency

  <dependency>
      <groupId>org.springframework.boot</groupId>                                                                     
      <artifactId>spring-boot-starter-data-elasticsearch</artifactId>                                                       
  </dependency>

  ---
Bước 2 — application.properties

spring.elasticsearch.uris=http://localhost:9200
spring.elasticsearch.username=elastic
spring.elasticsearch.password=changeme

  ---
Bước 3 — ProductDocument.java — Document lưu trong ES

Tạo file Document/ProductDocument.java:

@Document(indexName = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

      @Id
      private String productId;

      @Field(type = FieldType.Text, analyzer = "standard")
      private String name;

      @Field(type = FieldType.Text, analyzer = "standard")
      private String description;

      @Field(type = FieldType.Keyword)
      private String slug;

      @Field(type = FieldType.Keyword)
      private String thumbnail;

      @Field(type = FieldType.Long)
      private Long basePrice;

      @Field(type = FieldType.Long)
      private Long salePrice;

      @Field(type = FieldType.Double)
      private BigDecimal rating;

      @Field(type = FieldType.Integer)
      private Integer soldCount;

      @Field(type = FieldType.Keyword)
      private String categoryId;

      @Field(type = FieldType.Keyword)
      private String categoryName;

      @Field(type = FieldType.Keyword)
      private List<String> colors;  // lấy từ các variants

      @Field(type = FieldType.Keyword)
      private List<String> sizes;   // lấy từ các variants

      @Field(type = FieldType.Boolean)
      private Boolean isActive;

      @Field(type = FieldType.Boolean)
      private Boolean isDelete;

      @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
      private LocalDateTime createdAt;
}

  ---
Bước 4 — ProductSearchRepository.java

Tạo file Repository/ProductSearchRepository.java:

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
}

  ---
Bước 5 — Search DTOs

SearchProductItemResponse.java

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class SearchProductItemResponse {
private String productId;
private String name;
private String slug;
private String thumbnail;
private Long basePrice;
private Long salePrice;
private BigDecimal rating;
private Integer soldCount;
private Map<String, List<String>> highlight; // key: "name"/"description", value: highlighted fragments
}

CategoryAggregation.java

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class CategoryAggregation {
private String categoryId;
private String name;
private Long count;
}

PriceRangeAggregation.java

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class PriceRangeAggregation {
private Long from;
private Long to;
private Long count;
}

AggregationsResponse.java

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class AggregationsResponse {
private List<CategoryAggregation> categories;
private List<PriceRangeAggregation> priceRanges;
private List<String> colors;  // {"value": "Trắng", "count": 6}
private List<String> sizes;
}

PageSearchProductResponse.java

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class PageSearchProductResponse {
private List<SearchProductItemResponse> content;
private Integer page;
private Integer size;
private Long totalElements;
private Integer totalPages;
private Boolean isLast;
private AggregationsResponse aggregations;
}

  ---
Bước 6 — ProductSyncService.java — Đồng bộ DB → ES

Tạo file Service/ProductSyncService.java. Service này chịu trách nhiệm index/update/delete document trong ES mỗi khi DB thay đổi.

@Service
@RequiredArgsConstructor
public class ProductSyncService {

      private final ProductSearchRepository productSearchRepository;
      private final ProductRepo productRepo;

      public void indexProduct(Product product) {
          String thumbnail = product.getProductImages().stream()
                  .filter(ProductImage::getIsPrimary)
                  .map(ProductImage::getUrl)
                  .findFirst().orElse(null);

          List<String> colors = product.getProductVariants().stream()
                  .map(ProductVariant::getColor)
                  .filter(Objects::nonNull)
                  .distinct().toList();

          List<String> sizes = product.getProductVariants().stream()
                  .map(ProductVariant::getSize)
                  .filter(Objects::nonNull)
                  .distinct().toList();

          ProductDocument doc = ProductDocument.builder()
                  .productId(product.getId().toString())
                  .name(product.getName())
                  .description(product.getDescription())
                  .slug(product.getSlug())
                  .thumbnail(thumbnail)
                  .basePrice(product.getBasePrice())
                  .salePrice(product.getSalePrice())
                  .rating(product.getRating())
                  .soldCount(product.getSoldCount())
                  .categoryId(product.getCategory().getId().toString())
                  .categoryName(product.getCategory().getName())
                  .colors(colors)
                  .sizes(sizes)
                  .isActive(product.getIsActive())
                  .isDelete(product.getIsDelete())
                  .createdAt(product.getCreatedAt())
                  .build();

          productSearchRepository.save(doc);
      }

      public void deleteProduct(String productId) {
          productSearchRepository.deleteById(productId);
      }

      // Sync toàn bộ DB → ES (gọi khi cần reindex)
      public void syncAll() {
          List<Product> products = productRepo.findAll();
          products.forEach(this::indexProduct);
      }
}

Lưu ý: indexProduct cần fetch đầy đủ variants và images. Khi gọi từ ProductService, cần đảm bảo product đã có variants/images được load (dùng
findById với EntityGraph).

  ---
Bước 7 — ProductSearchService.java — Logic tìm kiếm

@Service
@RequiredArgsConstructor
public class ProductSearchService {

      private final ElasticsearchOperations elasticsearchOperations;

      public PageSearchProductResponse search(String q, Integer page, Integer size,
              String categoryId, Long minPrice, Long maxPrice,
              String color, String sizeFilter, String sort) {

          // 1. Build bool query
          Query boolQuery = Query.of(root -> root.bool(b -> {
              // Full-text search
              if (q != null && !q.isBlank()) {
                  b.must(m -> m.multiMatch(mm -> mm
                          .fields("name^3", "description")  // name được boost x3
                          .query(q)
                          .type(TextQueryType.BestFields)
                  ));
              } else {
                  b.must(m -> m.matchAll(ma -> ma)); // nếu không có q thì lấy tất cả
              }

              // Filters cố định
              b.filter(f -> f.term(t -> t.field("isActive").value(true)));
              b.filter(f -> f.term(t -> t.field("isDelete").value(false)));

              // Filters tùy chọn
              if (categoryId != null)
                  b.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));

              if (minPrice != null || maxPrice != null) {
                  b.filter(f -> f.range(r -> {
                      r.number(n -> {
                          n.field("basePrice");
                          if (minPrice != null) n.gte((double) minPrice);
                          if (maxPrice != null) n.lte((double) maxPrice);
                          return n;
                      });
                      return r;
                  }));
              }

              if (color != null)
                  b.filter(f -> f.term(t -> t.field("colors").value(color)));

              if (sizeFilter != null)
                  b.filter(f -> f.term(t -> t.field("sizes").value(sizeFilter)));

              return b;
          }));

          // 2. Build sort
          SortOptions sortOptions = switch (sort == null ? "relevance" : sort) {
              case "price_asc"  -> SortOptions.of(s -> s.field(f -> f.field("basePrice").order(SortOrder.Asc)));
              case "price_desc" -> SortOptions.of(s -> s.field(f -> f.field("basePrice").order(SortOrder.Desc)));
              case "newest"     -> SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)));
              case "bestseller" -> SortOptions.of(s -> s.field(f -> f.field("soldCount").order(SortOrder.Desc)));
              case "rating"     -> SortOptions.of(s -> s.field(f -> f.field("rating").order(SortOrder.Desc)));
              default           -> SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
          };

          // 3. Build highlight
          Highlight highlight = Highlight.of(h -> h
                  .fields("name", hf -> hf.numberOfFragments(0))
                  .fields("description", hf -> hf.numberOfFragments(1).fragmentSize(150))
                  .preTags("<em>")
                  .postTags("</em>")
          );

          // 4. Build NativeQuery
          NativeQuery nativeQuery = NativeQuery.builder()
                  .withQuery(boolQuery)
                  .withSort(sortOptions)
                  .withAggregation("categories", Aggregation.of(a -> a
                          .terms(t -> t.field("categoryId").size(20)
                                  .meta(Map.of("categoryName", JsonData.of("categoryName")))
                          )
                  ))
                  .withAggregation("colors", Aggregation.of(a -> a
                          .terms(t -> t.field("colors").size(50))
                  ))
                  .withAggregation("sizes", Aggregation.of(a -> a
                          .terms(t -> t.field("sizes").size(20))
                  ))
                  .withAggregation("price_ranges", Aggregation.of(a -> a
                          .range(r -> r.field("basePrice").ranges(
                                  AggregationRange.of(ar -> ar.to("200000")),
                                  AggregationRange.of(ar -> ar.from("200000").to("500000")),
                                  AggregationRange.of(ar -> ar.from("500000"))
                          ))
                  ))
                  .withHighlightQuery(new HighlightQuery(highlight, ProductDocument.class))
                  .withPageable(PageRequest.of(page, size))
                  .build();

          // 5. Execute
          SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

          // 6. Map results
          List<SearchProductItemResponse> content = hits.getSearchHits().stream()
                  .map(hit -> {
                      ProductDocument doc = hit.getContent();
                      return new SearchProductItemResponse(
                              doc.getProductId(),
                              doc.getName(),
                              doc.getSlug(),
                              doc.getThumbnail(),
                              doc.getBasePrice(),
                              doc.getSalePrice(),
                              doc.getRating(),
                              doc.getSoldCount(),
                              hit.getHighlightFields()
                      );
                  }).toList();

          // 7. Map aggregations
          AggregationsResponse aggregationsResponse = mapAggregations(hits.getAggregations());

          long totalElements = hits.getTotalHits();
          int totalPages = (int) Math.ceil((double) totalElements / size);

          return new PageSearchProductResponse(
                  content, page, size, totalElements, totalPages,
                  page >= totalPages - 1, aggregationsResponse
          );
      }

      private AggregationsResponse mapAggregations(ElasticsearchAggregations aggs) {
          if (aggs == null) return new AggregationsResponse();

          // Categories
          List<CategoryAggregation> categories = aggs.get("categories")
                  .aggregation().getAggregate().sterms().buckets().array().stream()
                  .map(b -> new CategoryAggregation(b.key().stringValue(), b.key().stringValue(), b.docCount()))
                  .toList();

          // Colors
          List<String> colors = aggs.get("colors")
                  .aggregation().getAggregate().sterms().buckets().array().stream()
                  .map(b -> b.key().stringValue())
                  .toList();

          // Sizes
          List<String> sizes = aggs.get("sizes")
                  .aggregation().getAggregate().sterms().buckets().array().stream()
                  .map(b -> b.key().stringValue())
                  .toList();

          // Price ranges
          List<PriceRangeAggregation> priceRanges = aggs.get("price_ranges")
                  .aggregation().getAggregate().range().buckets().array().stream()
                  .map(b -> new PriceRangeAggregation(
                          b.from() > 0 ? (long) b.from() : null,
                          b.to() < Double.MAX_VALUE ? (long) b.to() : null,
                          b.docCount()
                  ))
                  .toList();

          return new AggregationsResponse(categories, priceRanges, colors, sizes);
      }
}

  ---
Bước 8 — Update ProductService — Gọi sync sau mỗi thao tác

Inject ProductSyncService vào ProductService và gọi sau mỗi create/update/delete:

// Sau productRepo.save() trong createProduct:
productSyncService.indexProduct(save);

// Sau productRepo.save() trong updateProduct:
// Cần load lại product với đầy đủ relations trước khi sync
Product updated = productRepo.findByIdWithDetails(id); // dùng EntityGraph
productSyncService.indexProduct(updated);

// Trong deleteProduct:
product.setIsDelete(true);
productRepo.save(product);
productSyncService.deleteProduct(id.toString());

// Trong setIsActive:
productRepo.save(product);
productSyncService.indexProduct(product); // update isActive trong ES

  ---
Bước 9 — ProductController — Thêm endpoint

@GetMapping("/products/search")
public ResponseEntity<ApiResponse<PageSearchProductResponse>> searchProduct(
@RequestParam String q,
@RequestParam(defaultValue = "0") Integer page,
@RequestParam(defaultValue = "20") Integer size,
@RequestParam(required = false) String categoryId,
@RequestParam(required = false) Long minPrice,
@RequestParam(required = false) Long maxPrice,
@RequestParam(required = false) String color,
@RequestParam(required = false) String size_filter,
@RequestParam(defaultValue = "relevance") String sort
) {
return ResponseEntity.ok(ApiResponse.success(
"retrieved successfully",
productSearchService.search(q, page, size, categoryId, minPrice, maxPrice, color, size_filter, sort)
));
}

  ---
Thứ tự implement

pom.xml → dependency
application.properties → ES config
ProductDocument → document class
ProductSearchRepository → ES repository
DTOs (SearchProductItemResponse, AggregationsResponse, PageSearchProductResponse)
ProductSyncService → sync logic
ProductSearchService → search logic
ProductService → gọi sync sau create/update/delete
ProductController → endpoint /products/search

  ---
Lưu ý quan trọng

1. Conflict URL: GET /products/search và GET /products/{slug} — Spring sẽ match /products/search vào {slug}. Cần đặt endpoint /products/search
   trước /products/{slug} trong controller, hoặc đổi thành /products/search/query.

2. Sync variants/images: Khi indexProduct được gọi, cần đảm bảo product.getProductVariants() và product.getProductImages() đã được load. Thêm
   @EntityGraph riêng cho sync:
   @EntityGraph(attributePaths = {"productVariants", "productImages", "category"})
   Optional<Product> findByIdForSync(UUID id);

3. Reindex: Cần một admin endpoint để trigger syncAll() khi dữ liệu DB và ES bị lệch nhau.