package com.ice.searchservice.Service;

import com.ice.searchservice.Client.ProductClient;
import com.ice.searchservice.DTO.Event.Consume.ProductEventPayload;
import com.ice.searchservice.DTO.Redis.JobReindexRedis;
import com.ice.searchservice.DTO.Response.Product.ProductReindexPageResponse;
import com.ice.searchservice.Document.ProductDocument;
import com.ice.searchservice.Enum.JobReindexStatus;
import com.ice.searchservice.Repository.ProductSearchRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSyncService {

    private static final String REINDEX_PROCESS = "reindex:progress:";

    private final ProductSearchRepo productSearchRepo;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductClient productClient;

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

    @Async("searchExecutor")
    public void syncAll(String jobId) {
        try {
            int page = 0;
            while (true)
            {
                ProductReindexPageResponse productReindexPageResponse = productClient.getProducts(page);

                productReindexPageResponse.getContent().forEach(payload -> {
                    if(Boolean.TRUE.equals(payload.getIsDeleted()))
                        deleteProduct(payload.getProductId());
                    else
                        indexProduct(payload);
                });
                if(Boolean.TRUE.equals(productReindexPageResponse.getIsLast()))
                {
                    writeProgress(jobId, JobReindexStatus.DONE,
                            productReindexPageResponse.getTotalElements(),
                            productReindexPageResponse.getTotalElements());
                    break;
                }
                else
                    writeProgress(jobId, JobReindexStatus.RUNNING,
                            productReindexPageResponse.getTotalElements(),
                            (page + 1) * (long) ProductClient.PAGE_SIZE);

                page++;
            }
        } catch (Exception e) {
            log.error("Reindex job {} thất bại", jobId, e);
            writeProgress(jobId, JobReindexStatus.FAILED, 0L, 0L);
        }
    }

    private void writeProgress(String jobId, JobReindexStatus status, Long total, Long processed) {
        redisTemplate.opsForValue().set(
                REINDEX_PROCESS + jobId,
                new JobReindexRedis(status, total, processed),
                Duration.ofHours(24));
    }

}
