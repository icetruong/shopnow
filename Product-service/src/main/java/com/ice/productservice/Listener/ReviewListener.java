package com.ice.productservice.Listener;

import com.ice.productservice.DTO.Event.KafkaEvent;
import com.ice.productservice.DTO.Event.ReviewPostedPayload;
import com.ice.productservice.Service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewListener {
    private final ObjectMapper objectMapper;
    private final ProductService productService;

    @KafkaListener(topics = "review.posted", groupId = "product-service")
    public void handlePosted(String message)
    {
        KafkaEvent<ReviewPostedPayload> kafkaEvent;
        try {
            kafkaEvent = objectMapper.readValue(
                    message, new TypeReference<KafkaEvent<ReviewPostedPayload>>() {});
        } catch (Exception e) {
            // Message hỏng (không parse được) — retry cũng vô ích, log rồi bỏ qua
            // để không kẹt partition.
            log.error("Bỏ qua review.posted không hợp lệ: {}", message, e);
            return;
        }

        ReviewPostedPayload payload = kafkaEvent.getPayload();
        if (payload == null || payload.getProductId() == null) {
            log.error("review.posted thiếu payload/productId, bỏ qua: {}", message);
            return;
        }

        // Lỗi xử lý (DB/Redis) được ném ra ngoài để Kafka retry.
        productService.onUpdateProductForPostReview(payload, kafkaEvent.getEventId());
        log.info("Đã cập nhật rating cho product {} từ event {}",
                payload.getProductId(), kafkaEvent.getEventId());
    }
}
