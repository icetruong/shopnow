package com.ice.productservice;

import com.ice.productservice.DTO.Response.CategoryResponse;
import com.ice.productservice.DTO.Response.Common.ApiResponse;
import com.ice.productservice.Service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategory()
    {
        return ResponseEntity.ok(ApiResponse.success(
                "lấy thành công",
                categoryService.getAllCategory()
        ));
    }
}
