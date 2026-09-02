package com.ice.productservice.Service;

import com.ice.productservice.DTO.Event.KafkaEvent;
import com.ice.productservice.DTO.Event.StockChangedPayload;
import com.ice.productservice.Repository.ProductVariantRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {
    private final ProductVariantRepo productVariantRepo;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "stock.changed", groupId = "product-service")
    public void handleStockChanged(String message)
    {
        KafkaEvent<StockChangedPayload> event;
        try {
            event = objectMapper.readValue(
                    message, new TypeReference<KafkaEvent<StockChangedPayload>>() {});
        } catch (Exception e) {
            log.error("Bỏ qua stock.changed không hợp lệ: {}", message, e);
            return;
        }

        StockChangedPayload payload = event.getPayload();
        List<String> variantIds = payload == null ? null : payload.getVariantIds();
        if (variantIds == null || variantIds.isEmpty()) {
            log.warn("stock.changed không có variantIds, bỏ qua: {}", message);
            return;
        }

        variantIds.forEach(variantId ->
                productVariantRepo.findById(UUID.fromString(variantId))
                        .ifPresent(variant -> {
                            String slug = variant.getProduct().getSlug();
                            Cache cache = cacheManager.getCache("products");
                            if (cache != null)
                                cache.evict(slug);
                            log.info("Evicted cache for product: {}", slug);
                        }));
    }
}
