package com.ice.productservice.Controller.Internal;

import com.ice.productservice.DTO.Request.Internal.ProductRatingInternalRequest;
import com.ice.productservice.DTO.Response.Internal.ProductInternalResponse;
import com.ice.productservice.DTO.Response.Internal.ProductRatingInternalResponse;
import com.ice.productservice.DTO.Response.Internal.ProductVariantInternalResponse;
import com.ice.productservice.Service.ProductService;
import com.ice.productservice.Service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;
    private final ProductVariantService productVariantService;

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductInternalResponse> getProduct(@PathVariable UUID productId)
    {
        return ResponseEntity.ok(productService.getProductForInternal(productId));
    }

    @GetMapping("/products/{productId}/variants/{variantId}")
    public ResponseEntity<ProductVariantInternalResponse> getProductVariant(@PathVariable UUID productId,@PathVariable UUID variantId)
    {
        return ResponseEntity.ok(productVariantService.getProductAndVariantForInternal(productId, variantId));
    }

    @PostMapping("/products/rating")
    public ResponseEntity<ProductRatingInternalResponse> updateRating(@Valid @RequestBody ProductRatingInternalRequest request)
    {
        return ResponseEntity.ok(productService.updateRating(request));
    }

}
