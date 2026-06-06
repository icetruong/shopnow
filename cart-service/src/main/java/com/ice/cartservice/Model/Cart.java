package com.ice.cartservice.Model;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Cart {
    private String userId;
    private Instant updateAt;
    private Map<String, CartItem> items;
}
