package com.ice.searchservice.Service;

import com.ice.searchservice.DTO.Event.Consume.ProductEventPayload;
import com.ice.searchservice.Document.ProductDocument;
import com.ice.searchservice.Repository.ProductSearchRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchSyncService {

    private final ProductSearchRepo productSearchRepo;

    public void indexProduct(ProductEventPayload payload)
    {
        ProductDocument doc = ProductDocument.builder()
                .productId(payload.getProductId())
                .name(payload.getName())
                .description(payload.getDescription())
                .slug(payload.getSlug())
                .thumbnail(payload.getThumbnail())
                .basePrice(payload.getBasePrice())
                .salePrice(payload.getSalePrice())
                .rating(payload.getRating())
                .reviewCount(payload.getReviewCount())
                .soldCount(payload.getSoldCount())
                .categoryId(payload.getCategoryId())
                .categoryName(payload.getCategoryName())
                .colors(payload.getColors())
                .sizes(payload.getSizes())
                .isActive(payload.getIsActive())
                .isDeleted(payload.getIsDeleted())
                .createdAt(payload.getCreatedAt())
                .updatedAt(payload.getUpdatedAt())
                .build();

        productSearchRepo.save(doc);
    }

    public void deleteProduct(String productId)
    {
        productSearchRepo.deleteById(productId);
    }

    // TODO: reindex — gọi Product Service REST (GET /api/v1/products, paginate) rồi bulk index
    public void syncAll() {

    }
}
