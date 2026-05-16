package com.ice.productservice.Controller;

import com.ice.productservice.DTO.Request.Product.*;
import com.ice.productservice.DTO.Response.Common.ApiResponse;
import com.ice.productservice.DTO.Response.Product.PageProductResponse;
import com.ice.productservice.DTO.Response.Product.ProductCreatedResponse;
import com.ice.productservice.DTO.Response.Product.ProductDetailResponse;
import com.ice.productservice.DTO.Response.Product.VariantProductResponse;
import com.ice.productservice.Repository.ProductVariantRepo;
import com.ice.productservice.Service.ProductService;
import com.ice.productservice.Service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductController {
    private final ProductService productService;
    private final ProductVariantService productVariantService;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageProductResponse>> getAllProduct(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) Boolean isActive
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "retrieved successfully",
                        productService.getAllProduct(page, size, sort, direction, categoryId, minPrice, maxPrice, isActive)
                )
        );
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(@PathVariable String slug)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "retrieved successfully",
                        productService.getProduct(slug)
                )
        );
    }

    @PostMapping("/admin/products")
    public ResponseEntity<ApiResponse<ProductCreatedResponse>> createProduct(@Valid @RequestBody ProductRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "created successfully",
                        productService.createProduct(request)
                ));
    }

    @PutMapping("/admin/products/{id}")
    public ResponseEntity<ApiResponse<Void>> updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request)
    {

        productService.updateProduct(id, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "updated successfully",
                        null
                )
        );
    }

    @PatchMapping("/admin/products/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(@PathVariable UUID id,@Valid @RequestBody ProductSetIsActiveRequest request)
    {
        productService.setIsActive(id, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "updated successfully",
                        null
                )
        );
    }

    @DeleteMapping("/admin/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id)
    {
        productService.deleteProduct(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "deleted successfully",
                        null
                )
        );
    }

    @PostMapping("/admin/products/{productId}/variants")
    public ResponseEntity<ApiResponse<VariantProductResponse>> addVariants(@PathVariable UUID productId,@Valid @RequestBody CreateVariantProductRequest request)
    {
        return  ResponseEntity.ok(
                ApiResponse.success(
                        "added successfully",
                        productVariantService.createVariant(productId, request)
                )
        );
    }

    @PutMapping("/admin/products/{productId}/variants/{variantId}")
    public ResponseEntity<ApiResponse<Void>> updateVariants(@PathVariable UUID productId,@PathVariable UUID variantId,@Valid @RequestBody UpdateVariantProductRequest request)
    {
        productVariantService.updateVariant(variantId, productId, request);
        return  ResponseEntity.ok(
                ApiResponse.success(
                        "updated successfully",
                        null
                )
        );
    }

    @DeleteMapping("/admin/products/{productId}/variants/{variantId}")
    public ResponseEntity<ApiResponse<Void>> deleteVariants(@PathVariable UUID productId,@PathVariable UUID variantId)
    {
        productVariantService.deleteVariant(variantId, productId);
        return  ResponseEntity.ok(
                ApiResponse.success(
                        "deleted successfully",
                        null
                )
        );
    }
}
