package com.ice.inventoryservice.DTO.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KafkaEvent<T> {
    private String eventId;
    private String eventType;
    private String timestamp;
    private String version;
    private T payload;
}
