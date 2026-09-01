package com.ice.searchservice.DTO.Response.Product;

import com.ice.searchservice.DTO.Event.Consume.ProductEventPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductReindexPageResponse {
    private List<ProductEventPayload> content;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private Boolean isLast;
}
