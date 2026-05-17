package com.ice.productservice.DTO.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductEvent {
    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String version;
    private ProductEventPayload payload;
}
