package com.ice.productservice.Service;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.ice.productservice.DTO.Response.Search.*;
import com.ice.productservice.Document.ProductDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductSearchService {
    private final ElasticsearchOperations elasticsearchOperations;

    public PageSearchProductResponse search(String q, Integer page, Integer size, String categoryId, Long minPrice, Long maxPrice,
                                            String color, String sizeFilter, String sort)
    {
        // build Query
        Query boolQuery = Query.of(root -> root.bool(b -> {
            if (q != null && !q.isBlank()) {
                b.must(m -> m.multiMatch(builder -> builder
                        .fields("name^3", "description")
                        .query(q)
                        .type(TextQueryType.BestFields)
                ));
            } else {
                b.must(m -> m.matchAll(builder -> builder));
            }

            b.filter(f -> f.term(t -> t.field("isActive").value(true)));
            b.filter(f -> f.term(t -> t.field("isDelete").value(false)));

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

        // build Sort
        SortOptions sortOptions = switch (sort == null ? "relevence" : sort)
        {
            case "price_asc" -> SortOptions.of(s -> s.field(f -> f.field("basePrice").order(SortOrder.Asc) ));
            case "price_desc" -> SortOptions.of(s -> s.field(f -> f.field("basePrice").order(SortOrder.Desc)));
            case "newest" -> SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)));
            case "bestseller" -> SortOptions.of(s -> s.field(f -> f.field("soldCount").order(SortOrder.Desc)));
            case "rating" -> SortOptions.of(s -> s.field(f -> f.field("rating").order(SortOrder.Desc)));
            default -> SortOptions.of(s-> s.score(sc -> sc.order(SortOrder.Desc)));
        };

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
                        "categories",
                        Aggregation.of(a -> a
                                .terms(t -> t.field("categoryId").size(20))
                        )
                )
                .withAggregation(
                        "price_ranges",
                        Aggregation.of( a -> a
                                .range(r -> r.field("basePrice").ranges(
                                        AggregationRange.of(ar -> ar.to(200000.0)),
                                        AggregationRange.of(ar -> ar.from(200000.0).to(500000.0)),
                                        AggregationRange.of(ar -> ar.from(500000.0))
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
                .withHighlightQuery(new HighlightQuery(highlight, ProductDocument.class))
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        List<SearchProductResponse> content = hits.getSearchHits().stream()
                .map(searchHit -> new SearchProductResponse(
                        searchHit.getContent().getProductId(),
                        searchHit.getContent().getName(),
                        searchHit.getContent().getSlug(),
                        searchHit.getContent().getThumbnail(),
                        searchHit.getContent().getBasePrice(),
                        searchHit.getContent().getSalePrice(),
                        searchHit.getContent().getRating(),
                        searchHit.getContent().getSoldCount(),
                        searchHit.getHighlightFields()
                )).toList();


        AggregationsResponse aggregationsResponse = toAggregationsResponse((ElasticsearchAggregations) hits.getAggregations());

        long totalElements = hits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return new PageSearchProductResponse(
                content,
                page,
                size,totalElements,
                totalPages,
                page >= totalPages - 1,
                aggregationsResponse
        );
    }

    public List<String> suggest(String q, int size)
    {
        if(q == null || q.isBlank())
            return List.of();

        Query query = Query.of(
                root -> root.bool(
                        b -> {
                            b.
                                    must(m -> m.multiMatch(builder -> builder
                                            .fields("nameSuggest", "nameSuggest._2gram", "nameSuggest._3gram")
                                            .query(q)
                                            .type(TextQueryType.BoolPrefix)
                                    ));
                            b.filter(f -> f.term(t -> t.field("isActive").value(true)));
                            b.filter(f -> f.term(t -> t.field("isDelete").value(false)));

                            return b;
                        }

                ));

        int clampedSize = Math.min(size, 10);
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withSourceFilter(new FetchSourceFilter(true, new String[] {"name"}, null))
                .withPageable(PageRequest.of(0, clampedSize))
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
        return hits.getSearchHits().stream()
                .map(hit -> hit.getContent().getName())
                .toList();

    }

    private AggregationsResponse toAggregationsResponse(ElasticsearchAggregations aggregations)
    {
        if (aggregations == null)
            return new AggregationsResponse();

        List<CategoryAggregation> categoryAggregations = Objects.requireNonNull(aggregations.get("categories"))
                .aggregation().getAggregate().sterms().buckets().array().stream()
                .map(b -> new CategoryAggregation(
                        b.key().stringValue(),
                        b.key().stringValue(),
                        b.docCount()
                ))
                .toList();

        List<PriceRangeAggregation> priceRangeAggregations = Objects.requireNonNull(aggregations.get("price_ranges"))
                .aggregation().getAggregate().range().buckets().array().stream()
                .map(b -> new PriceRangeAggregation(
                        b.from() != null && b.from() > 0 ? (long) b.from().doubleValue() : null,
                        b.to() != null && b.to() < Double.MAX_VALUE ? (long) b.to().doubleValue() : null,
                        b.docCount()
                )).toList();

        List<ColorAggregation> colorAggregations = Objects.requireNonNull(aggregations.get("colors"))
                .aggregation().getAggregate().sterms().buckets().array().stream()
                .map(b -> new ColorAggregation(
                        b.key().stringValue(),
                        b.docCount()
                )).toList();

        List<String> sizes = Objects.requireNonNull(aggregations.get("sizes"))
                .aggregation().getAggregate().sterms().buckets().array().stream()
                .map(b -> b.key().stringValue()).toList();

        return new AggregationsResponse(
                categoryAggregations,
                priceRangeAggregations,
                colorAggregations,
                sizes
        );
    }
}