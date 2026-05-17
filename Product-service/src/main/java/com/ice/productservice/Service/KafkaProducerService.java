package com.ice.productservice.Service;

import com.ice.productservice.DTO.Event.ProductEvent;
import com.ice.productservice.DTO.Event.ProductEventPayload;
import com.ice.productservice.Entity.Product;
import com.ice.productservice.Entity.ProductImage;
import com.ice.productservice.Entity.ProductVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private static final String TOPIC = "product.updated";


    public void publish(Product product)
    {
        String thumbnail = product.getProductImages().stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(null);

        List<String> colors = product.getProductVariants().stream()
                .map(ProductVariant::getColor)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<String> sizes = product.getProductVariants().stream()
                .map(ProductVariant::getSize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        ProductEventPayload payload = new ProductEventPayload(
                product.getId().toString(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getCategory().getId().toString(),
                product.getCategory().getName(),
                product.getBasePrice(),
                product.getSalePrice(),
                product.getRating(),
                product.getSoldCount(),
                thumbnail,
                product.getIsActive(),
                product.getIsDelete(),
                colors,
                sizes,
                product.getUpdatedAt()
        );

        ProductEvent event = new ProductEvent(
                UUID.randomUUID().toString(),
                TOPIC,
                LocalDateTime.now(),
                "1.0",
                payload
        );

        kafkaTemplate.send(TOPIC, product.getId().toString(), event);
    }

}
