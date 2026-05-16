package com.ice.productservice.DTO.Request.Image;

import jakarta.validation.Valid;
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
public class OrderImageRequest {
    @Valid
    @NotEmpty(message = "orders is not empty")
    private List<ImageRequest> orders;
}
