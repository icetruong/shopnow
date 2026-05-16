package com.ice.productservice.Controller;

import com.ice.productservice.DTO.Request.Product.ProductRequest;
import com.ice.productservice.DTO.Request.Product.ProductSetIsActiveRequest;
import com.ice.productservice.DTO.Request.Product.ProductUpdateRequest;
import com.ice.productservice.DTO.Response.Common.ApiResponse;
import com.ice.productservice.DTO.Response.Product.PageProductResponse;
import com.ice.productservice.DTO.Response.Product.ProductCreatedResponse;
import com.ice.productservice.DTO.Response.Product.ProductDetailResponse;
import com.ice.productservice.Service.ProductService;
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
                        "lấy thành công",
                        productService.getAllProduct(page, size, sort, direction, categoryId, minPrice, maxPrice, isActive)
                )
        );
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(@PathVariable String slug)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "lấy thành công",
                        productService.getProduct(slug)
                )
        );
    }

    @PostMapping("/admin/products")
    public ResponseEntity<ApiResponse<ProductCreatedResponse>> createProduct(@Valid @RequestBody ProductRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "tạo thành công",
                        productService.createProduct(request)
                ));
    }

    @PutMapping("/admin/products/{id}")
    public ResponseEntity<ApiResponse<Void>> updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request)
    {

        productService.updateProduct(id, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "cập nhập thành công",
                        null
                )
        );
    }

    @PatchMapping("/admin/products/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(@PathVariable UUID id,@RequestBody ProductSetIsActiveRequest request)
    {
        productService.setIsActive(id, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "cập nhập thành công",
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
                        "xóa thành công",
                        null
                )
        );
    }
}
