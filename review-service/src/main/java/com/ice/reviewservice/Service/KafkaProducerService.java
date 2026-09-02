package com.ice.reviewservice.Service;

import com.ice.reviewservice.DTO.Event.Publish.KafkaEvent;
import com.ice.reviewservice.DTO.Event.Publish.ReviewPostedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String REVIEW_POSTED = "review.posted";

    public void publishReviewPostedEvent(ReviewPostedPayload payload)
    {
        KafkaEvent<ReviewPostedPayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                REVIEW_POSTED,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(REVIEW_POSTED, payload.getProductId(), event);
    }
}
