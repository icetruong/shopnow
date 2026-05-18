package com.ice.productservice.Controller;

import com.ice.productservice.DTO.Request.Category.CategoryRequest;
import com.ice.productservice.DTO.Response.Admin.CategoryAdminResponse;
import com.ice.productservice.DTO.Response.Category.CategoryResponse;
import com.ice.productservice.DTO.Response.Common.ApiResponse;
import com.ice.productservice.Service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategory()
    {
        return ResponseEntity.ok(ApiResponse.success(
                "retrieved successfully",
                categoryService.getAllCategory()
        ));
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<ApiResponse<CategoryAdminResponse>> addCategory(@Valid @RequestBody CategoryRequest categoryRequest)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "created successfully",
                        categoryService.addCategory(categoryRequest)
                ));
    }

    @PutMapping("/admin/categories/{id}")
    public ResponseEntity<ApiResponse<Void>> updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request)
    {
        categoryService.updateCategory(id,request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "updated successfully",
                        null
                )
        );
    }

    @DeleteMapping("/admin/categories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id)
    {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "deleted successfully",
                        null
                )
        );
    }

}
