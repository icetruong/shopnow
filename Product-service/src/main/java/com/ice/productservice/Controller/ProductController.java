package com.ice.productservice.Controller;

import com.ice.productservice.DTO.Request.Image.OrderImageRequest;
import com.ice.productservice.DTO.Request.Product.*;
import com.ice.productservice.DTO.Response.Common.ApiResponse;
import com.ice.productservice.DTO.Response.Image.ImageUploadResponse;
import com.ice.productservice.DTO.Response.Product.PageProductResponse;
import com.ice.productservice.DTO.Response.Product.ProductCreatedResponse;
import com.ice.productservice.DTO.Response.Product.ProductDetailResponse;
import com.ice.productservice.DTO.Response.Product.VariantProductResponse;
import com.ice.productservice.DTO.Response.Search.PageSearchProductResponse;
import com.ice.productservice.Service.ProductImageService;
import com.ice.productservice.Service.ProductSearchService;
import com.ice.productservice.Service.ProductService;
import com.ice.productservice.Service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductController {
    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final ProductImageService productImageService;
    private final ProductSearchService productSearchService;

    // TODO: Tách sang Search Service — endpoint search sản phẩm không thuộc Product Service
    @GetMapping("/products/search")
    public ResponseEntity<ApiResponse<PageSearchProductResponse>> searchProduct(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String color,
            @RequestParam(value = "sizeFilter", required = false) String sizeFilter,
            @RequestParam(defaultValue = "relevance") String sort
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "retrieved successfully",
                        productSearchService.search(q, page, size, categoryId, minPrice, maxPrice, color, sizeFilter, sort)
                )
        );
    }

    // TODO: Tách sang Search Service — endpoint suggest/autocomplete không thuộc Product Service
    @GetMapping("/products/search/suggest")
    public ResponseEntity<ApiResponse<List<String>>> suggestProduct(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") Integer size
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "retrieved successfully",
                        productSearchService.suggest(q, size)
                )
        );

    }


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
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "retrieved successfully",
                        productService.getAllProduct(page, size, sort, direction, categoryId, minPrice, maxPrice, isActive)
                )
        );
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(@PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "retrieved successfully",
                        productService.getProduct(slug)
                )
        );
    }

    @PostMapping("/admin/products")
    public ResponseEntity<ApiResponse<ProductCreatedResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "created successfully",
                        productService.createProduct(request)
                ));
    }

    @PutMapping("/admin/products/{id}")
    public ResponseEntity<ApiResponse<Void>> updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request) {

        productService.updateProduct(id, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "updated successfully",
                        null
                )
        );
    }

    @PatchMapping("/admin/products/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(@PathVariable UUID id, @Valid @RequestBody ProductSetIsActiveRequest request) {
        productService.setIsActive(id, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "updated successfully",
                        null
                )
        );
    }

    @DeleteMapping("/admin/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "deleted successfully",
                        null
                )
        );
    }

    @PostMapping("/admin/products/{productId}/variants")
    public ResponseEntity<ApiResponse<VariantProductResponse>> addVariants(@PathVariable UUID productId, @Valid @RequestBody CreateVariantProductRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "added successfully",
                        productVariantService.createVariant(productId, request)
                )
        );
    }

    @PutMapping("/admin/products/{productId}/variants/{variantId}")
    public ResponseEntity<ApiResponse<Void>> updateVariants(@PathVariable UUID productId, @PathVariable UUID variantId, @Valid @RequestBody UpdateVariantProductRequest request) {
        productVariantService.updateVariant(variantId, productId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "updated successfully",
                        null
                )
        );
    }

    @DeleteMapping("/admin/products/{productId}/variants/{variantId}")
    public ResponseEntity<ApiResponse<Void>> deleteVariants(@PathVariable UUID productId, @PathVariable UUID variantId) {
        productVariantService.deleteVariant(variantId, productId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "deleted successfully",
                        null
                )
        );
    }

    @PostMapping("/admin/products/{productId}/images")
    public ResponseEntity<ApiResponse<List<ImageUploadResponse>>> uploadImages(
            @PathVariable UUID productId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart(value = "altTexts", required = false) List<String> altTexts) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "uploaded successfully",
                        productImageService.uploadImage(productId, files, altTexts)
                )
        );
    }

    @PatchMapping("/admin/products/{productId}/images/{imageId}/primary")
    public ResponseEntity<ApiResponse<Void>> updatePrimary(@PathVariable UUID productId, @PathVariable UUID imageId)
    {
        productImageService.updatePrimary(productId, imageId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "updated successfully",
                        null
                )
        );
    }

    @PatchMapping("/admin/products/{productId}/images/sort")
    public ResponseEntity<ApiResponse<Void>> sortImage(@PathVariable UUID productId, @Valid @RequestBody OrderImageRequest request)
    {
        productImageService.updateSort(productId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "sorted successfully",
                        null
                )
        );
    }

    @DeleteMapping("/admin/products/{productId}/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(@PathVariable UUID productId, @PathVariable UUID imageId)
    {
        productImageService.deleteImage(productId, imageId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "deleted successfully",
                        null
                )
        );
    }
}
