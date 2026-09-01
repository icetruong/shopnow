package com.ice.productservice.DTO.Response.Internal;

import com.ice.productservice.DTO.Event.ProductEventPayload;
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
