package com.qlda.userservice.Event;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class KafkaEvent<T> {
    private String eventId;
    private String eventType;
    private String timestamp;
    private String version;
    private T payload;

    public static <T> KafkaEvent<T> of(String eventType, T payload) {
        return KafkaEvent.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .timestamp(Instant.now().toString())
                .version("1.0")
                .payload(payload)
                .build();
    }

}
