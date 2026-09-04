package com.ice.productservice.DTO.Request.Internal;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductBatchRequest {
    @NotEmpty(message = "variantIds not empty")
    private List<String> variantIds;
}
