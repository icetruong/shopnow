package com.ice.productservice.Service;

import com.ice.productservice.Repository.ProductVariantRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {
    private final ProductVariantRepo productVariantRepo;
    private final CacheManager cacheManager;

    @KafkaListener(topics = "stock.changed", groupId = "product-service")
    public void handleStockChanged(Map<String, Object> event)
    {
        try
        {
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            List<String> variantIds = (List<String>) payload.get("variantIds");

            variantIds.forEach(
                    variantId -> productVariantRepo.findById(UUID.fromString(variantId))
                            .ifPresent(variant -> {
                                String slug = variant.getProduct().getSlug();
                                Cache cache = cacheManager.getCache("products");
                                if(cache != null)
                                    cache.evict(slug);
                                log.info("Evicted cache for product: {}", slug);
                            })
            );
        }
        catch (Exception e)
        {
            log.error("Failed to process stock.changed event: {}", e.getMessage());
        }
    }
}
