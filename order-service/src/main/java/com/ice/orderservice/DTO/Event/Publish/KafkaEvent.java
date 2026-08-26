package com.ice.orderservice.DTO.Event.Publish;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KafkaEvent<T> {
    private String eventId;
    private String eventType;
    private String timestamp;
    private String version;
    private T payload;
}
