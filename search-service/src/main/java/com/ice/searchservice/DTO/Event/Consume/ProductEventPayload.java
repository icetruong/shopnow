package com.ice.searchservice.DTO.Event.Consume;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductEventPayload {
    private String productId;
    private String name;
    private String slug;
    private String description;
    private String categoryId;
    private String categoryName;
    private Long basePrice;
    private Long salePrice;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer soldCount;
    private String thumbnail;
    private Boolean isActive;
    private Boolean isDeleted;
    private List<String> colors;
    private List<String> sizes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
