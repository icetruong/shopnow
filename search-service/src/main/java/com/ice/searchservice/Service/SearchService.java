package com.ice.searchservice.Service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.ice.searchservice.DTO.Redis.JobReindexRedis;
import com.ice.searchservice.DTO.Response.Search.*;
import com.ice.searchservice.Document.ProductDocument;
import com.ice.searchservice.Enum.JobReindexStatus;
import com.ice.searchservice.Enum.SortOption;
import com.ice.searchservice.Exception.ElasticsearchUnavailableException;
import com.ice.searchservice.Exception.InvalidSortOptionException;
import com.ice.searchservice.Exception.ResourceNotFoundException;
import com.ice.searchservice.Exception.SearchQueryTooLongException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    private static final String REINDEX_PROCESS = "reindex:progress:";
    private static final long PRICE_TIER_1 = 200_000L;
    private static final long PRICE_TIER_2 = 500_000L;
    private static final String PRICE_FIELD = "basePrice";
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_SUGGESTIONS = 8;

    private final ElasticsearchOperations elasticsearchOperations;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SearchSyncService searchSyncService;
    private final TrendingService trendingService;

    public PageSearchProductResponse search(String q, Integer page, Integer size, String categoryId, Long minPrice, Long maxPrice,
                                            List<String> colors, List<String> sizes, Double minRating, String sort)
    {
        if (q != null && q.length() > MAX_QUERY_LENGTH)
            throw new SearchQueryTooLongException(q);     // → 400 SEARCH_QUERY_TOO_LONG

        // sort không hợp lệ
        if (!SortOption.isValid(sort))
            throw new InvalidSortOptionException(sort);  // → 400 INVALID_SORT_OPTION

        // chuẩn hoá phân trang (bảo vệ ES)
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);


        // build Query
        Query boolQuery = Query.of(root -> root.bool(b -> {
            if (q != null && !q.isBlank()) {
                b.must(m -> m.multiMatch(builder -> builder
                        .fields("name^3", "description")
                        .query(q)
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")
                        .operator(Operator.And)
                ));
            } else {
                b.must(m -> m.matchAll(builder -> builder));
            }

            b.filter(f -> f.term(t -> t.field("isActive").value(true)));
            b.filter(f -> f.term(t -> t.field("isDeleted").value(false)));

            if (categoryId != null)
                b.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));

            if (minPrice != null || maxPrice != null) {
                b.filter(f -> f.range(r -> {
                    r.number(n -> {
                        n.field(PRICE_FIELD);
                        if (minPrice != null) n.gte((double) minPrice);
                        if (maxPrice != null) n.lte((double) maxPrice);
                        return n;
                    });
                    return r;
                }));
            }

            if (minRating != null)
                b.filter(f -> f.range(r -> r.number(n -> n.field("rating").gte(minRating))));

            if (colors != null && !colors.isEmpty())
                b.filter(f -> f.terms(t -> t.field("colors")
                        .terms(tv -> tv.value(colors.stream().map(FieldValue::of).toList()))));

            if (sizes != null && !sizes.isEmpty())
                b.filter(f -> f.terms(t -> t.field("sizes")
                        .terms(tv -> tv.value(sizes.stream().map(FieldValue::of).toList()))));

            return b;
        }));

        // build Sort — primary + tiebreaker để phân trang from/size ổn định
        List<SortOptions> sortOptions = buildSort(SortOption.from(sort));

        // build hightligh
        Highlight highlight = new Highlight(
                HighlightParameters.builder()
                        .withPreTags("<em>")
                        .withPostTags("</em>")
                        .build(),
                List.of(
                        new HighlightField("name",
                                HighlightFieldParameters.builder()
                                        .withNumberOfFragments(0)
                                        .build()
                                ),
                        new HighlightField("description",
                                HighlightFieldParameters.builder()
                                        .withNumberOfFragments(1)
                                        .withFragmentSize(150)
                                        .build()
                                )
                )
        );

        // build native query
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .withSort(sortOptions)
                .withAggregation(
                        "price_ranges",
                        Aggregation.of( a -> a
                                .range(r -> r.field(PRICE_FIELD).ranges(
                                        AggregationRange.of(ar -> ar.to((double) PRICE_TIER_1)),
                                        AggregationRange.of(ar -> ar.from((double) PRICE_TIER_1).to((double) PRICE_TIER_2)),
                                        AggregationRange.of(ar -> ar.from((double) PRICE_TIER_2))
                                ))

                        )
                )
                .withAggregation(
                        "sizes",
                        Aggregation.of(a -> a
                                .terms(t -> t.field("sizes").size(20))
                        )
                )
                .withAggregation(
                        "colors",
                        Aggregation.of(a -> a
                                .terms(t -> t.field("colors").size(50))
                        )
                )
                .withAggregation("ratings", Aggregation.of(a -> a.range(r -> r
                        .field("rating")
                        .ranges(
                                AggregationRange.of(x -> x.key("5").from(5.0)),
                                AggregationRange.of(x -> x.key("4").from(4.0)),
                                AggregationRange.of(x -> x.key("3").from(3.0)),
                                AggregationRange.of(x -> x.key("2").from(2.0)),
                                AggregationRange.of(x -> x.key("1").from(1.0))
                        ))))
                .withAggregation("categories",
                        Aggregation.of(a -> a
                                .terms(t -> t.field("categoryId").size(20))
                                .aggregations("catName", sub -> sub.terms(tt -> tt.field("categoryName").size(1)))

                ))
                .withHighlightQuery(new HighlightQuery(highlight, ProductDocument.class))
                .withPageable(PageRequest.of(safePage, safeSize))
                .build();

        SearchHits<ProductDocument> hits;
        long t0 = System.currentTimeMillis();
        try {
            hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
        } catch (DataAccessException | co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            log.error("Elasticsearch lỗi khi search: q={}", q, e);
            throw new ElasticsearchUnavailableException("Elasticsearch không phản hồi");   // → 503 ELASTICSEARCH_UNAVAILABLE
        }
        long took = System.currentTimeMillis() - t0;

        List<SearchProductResponse> content = hits.getSearchHits().stream()
                .map(searchHit -> {
                    ProductDocument doc = searchHit.getContent();
                    return new SearchProductResponse(
                            doc.getProductId(),
                            doc.getName(),
                            doc.getSlug(),
                            doc.getThumbnail(),
                            doc.getBasePrice(),
                            doc.getSalePrice(),
                            computeDiscountPct(doc.getBasePrice(), doc.getSalePrice()),
                            doc.getRating(),
                            doc.getReviewCount(),
                            doc.getSoldCount(),
                            doc.getCategoryName(),
                            searchHit.getHighlightFields()
                    );
                }).toList();


        AggregationsResponse aggregationsResponse = toAggregationsResponse((ElasticsearchAggregations) hits.getAggregations());

        long totalElements = hits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);

        trendingService.recordSearchTerm(q);

        return new PageSearchProductResponse(
                content,
                safePage,
                safeSize,
                totalElements,
                totalPages,
                safePage >= totalPages - 1,
                took,
                aggregationsResponse
        );
    }

    public SuggestResponse suggest(String q, int size)
    {
        if (q == null || q.isBlank())
            return new SuggestResponse(List.of(), List.of());   // KHÔNG trả null

        if (q.length() > MAX_QUERY_LENGTH)
            throw new SearchQueryTooLongException(q);     // → 400 SEARCH_QUERY_TOO_LONG

        int clampedSize = Math.min(Math.max(size, 1), 10);

        Query query = Query.of(
                root -> root.bool(
                        b -> {
                            b.
                                    must(m -> m.multiMatch(builder -> builder
                                            .fields("name.suggest", "name.suggest._2gram", "name.suggest._3gram")
                                            .query(q)
                                            .type(TextQueryType.BoolPrefix)
                                    ));
                            b.filter(f -> f.term(t -> t.field("isActive").value(true)));
                            b.filter(f -> f.term(t -> t.field("isDeleted").value(false)));

                            return b;
                        }

                ));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withSourceFilter(new FetchSourceFilter(true, new String[] { "productId", "name", "slug", "thumbnail", "salePrice"}, null))
                .withPageable(PageRequest.of(0, clampedSize))
                .build();

        SearchHits<ProductDocument> hits;
        try {
            hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
        } catch (DataAccessException | co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            log.error("Elasticsearch lỗi khi suggest: q={}", q, e);
            throw new ElasticsearchUnavailableException("Elasticsearch không phản hồi");   // → 503 ELASTICSEARCH_UNAVAILABLE
        }

        List<ProductDocument> productDocuments = hits.getSearchHits()
                .stream().map(SearchHit::getContent)
                .toList();

        return new SuggestResponse(
                productDocuments.stream().map(ProductDocument::getName)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .distinct()
                        .limit(MAX_SUGGESTIONS)
                        .map(name -> new SuggestionItem(name, "keyword"))
                        .toList(),
                productDocuments.stream().map(
                        productDocument -> new SuggestProductItem(
                                productDocument.getProductId(),
                                productDocument.getName(),
                                productDocument.getSlug(),
                                productDocument.getThumbnail(),
                                productDocument.getSalePrice()
                        )
                ).toList()
        );
    }

    public JobReindexResponse startReindex()
    {
        String jobId = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(REINDEX_PROCESS+jobId, new JobReindexRedis(
                JobReindexStatus.RUNNING, 0L, 0L), Duration.ofHours(24));

        searchSyncService.syncAll(jobId);

        return new JobReindexResponse(jobId);
    }


    public JobReindexStatusResponse getReindex(String jobId)
    {
        JobReindexRedis jobReindexRedis =(JobReindexRedis) redisTemplate.opsForValue().get(REINDEX_PROCESS+jobId);
        if (jobReindexRedis == null)
            throw new ResourceNotFoundException("không tìm thấy jobReindexRedis trong reids" + jobId);
        Long total = jobReindexRedis.getTotal();
        Long processed = jobReindexRedis.getProcessed();
        long progress = (total == null || total == 0) ? 0 : processed * 100 / total;
        return new JobReindexStatusResponse(
                jobId,
                jobReindexRedis.getStatus().toString(),
                total,
                processed,
                progress
        );
    }

    private List<SortOptions> buildSort(SortOption sort) {
        SortOptions tieBreak = SortOptions.of(s -> s.field(f -> f.field("productId").order(SortOrder.Asc)));

        SortOptions primary = switch (sort) {
            case PRICE_ASC  -> SortOptions.of(s -> s.field(f -> f.field(PRICE_FIELD).order(SortOrder.Asc)));
            case PRICE_DESC -> SortOptions.of(s -> s.field(f -> f.field(PRICE_FIELD).order(SortOrder.Desc)));
            case NEWEST     -> SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)));
            case BESTSELLER -> SortOptions.of(s -> s.field(f -> f.field("soldCount").order(SortOrder.Desc)));
            case RATING     -> SortOptions.of(s -> s.field(f -> f.field("rating").order(SortOrder.Desc)));
            case RELEVANCE  -> SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
        };

        // rating cần tiebreaker theo reviewCount (spec), các case khác chỉ cần productId
        if (sort == SortOption.RATING) {
            SortOptions byReview = SortOptions.of(s -> s.field(f -> f.field("reviewCount").order(SortOrder.Desc)));
            return List.of(primary, byReview, tieBreak);
        }
        return List.of(primary, tieBreak);
    }

    private AggregationsResponse toAggregationsResponse(ElasticsearchAggregations aggregations)
    {
        if (aggregations == null)
            return new AggregationsResponse();

        List<CategoryAggregation> categoryAggregations = Objects.requireNonNull(aggregations.get("categories"))
                .aggregation().getAggregate().sterms().buckets().array().stream()
                .map(b -> {
                    String id = b.key().stringValue();
                    return new CategoryAggregation(id, resolveCategoryName(b, id), b.docCount());
                })
                .toList();

        List<PriceRangeAggregation> priceRangeAggregations = Objects.requireNonNull(aggregations.get("price_ranges"))
                .aggregation().getAggregate().range().buckets().array().stream()
                .map(b -> {
                    Long from = b.from() != null && b.from() > 0 ? (long) b.from().doubleValue() : null;
                    Long to = b.to() != null && b.to() < Double.MAX_VALUE ? (long) b.to().doubleValue() : null;
                    return new PriceRangeAggregation(priceLabel(from, to), from, to, b.docCount());
                }).toList();

        List<ColorAggregation> colorAggregations = Objects.requireNonNull(aggregations.get("colors"))
                .aggregation().getAggregate().sterms().buckets().array().stream()
                .map(b -> new ColorAggregation(b.key().stringValue(), b.docCount()))
                .toList();

        List<String> sizes = Objects.requireNonNull(aggregations.get("sizes"))
                .aggregation().getAggregate().sterms().buckets().array().stream()
                .map(b -> b.key().stringValue()).toList();

        List<RatingAggregation> ratingAggregations = Objects.requireNonNull(aggregations.get("ratings"))
                .aggregation().getAggregate().range().buckets().array().stream()
                .filter(b -> b.key() != null && b.docCount() > 0)
                .map(b -> new RatingAggregation(Integer.valueOf(b.key()), b.docCount()))
                .toList();

        return new AggregationsResponse(
                categoryAggregations,
                priceRangeAggregations,
                colorAggregations,
                sizes,
                ratingAggregations
        );
    }

    // lấy categoryName từ sub-aggregation "catName" (terms size 1), fallback về id nếu thiếu
    private static String resolveCategoryName(StringTermsBucket bucket, String fallback) {
        var catName = bucket.aggregations().get("catName");
        if (catName == null)
            return fallback;
        return catName.sterms().buckets().array().stream()
                .findFirst()
                .map(x -> x.key().stringValue())
                .orElse(fallback);
    }

    // % giảm giá làm tròn; 0 nếu không có salePrice hợp lệ
    private static Integer computeDiscountPct(Long basePrice, Long salePrice) {
        if (basePrice == null || basePrice <= 0 || salePrice == null || salePrice >= basePrice)
            return 0;
        return (int) Math.round((basePrice - salePrice) * 100.0 / basePrice);
    }

    private static String priceLabel(Long from, Long to) {
        if (from == null) return "Dưới " + toK(to);
        if (to == null) return "Trên " + toK(from);
        return toK(from) + " - " + toK(to);
    }

    private static String toK(Long value) {
        return (value / 1000) + "k";
    }
}