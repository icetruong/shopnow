package com.ice.productservice.Service;

import com.ice.productservice.DTO.Request.Category.CategoryRequest;
import com.ice.productservice.DTO.Response.Admin.CategoryAdminResponse;
import com.ice.productservice.DTO.Response.Category.CategoryResponse;
import com.ice.productservice.Entity.Category;
import com.ice.productservice.Exception.CategoryHasCategoryChildrenException;
import com.ice.productservice.Exception.CategoryHasProductException;
import com.ice.productservice.Exception.ResourceNotFoundException;
import com.ice.productservice.Repository.CategoryRepo;
import com.ice.productservice.Repository.ProductRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepo categoryRepo;
    private final ProductRepo productRepo;

    @Cacheable(value = "categories", key = "'all'")
    public List<CategoryResponse> getAllCategory()
    {
        List<Category> categories = categoryRepo.findAllActiveForTree();

        Map<String, CategoryResponse> map = new HashMap<>();

        categories.forEach(category -> {
            map.put(category.getId().toString(), toCategoryResponseFromCategory(category));
        });
        List<CategoryResponse> categoryResponses = new ArrayList<>();
        categories.forEach(category -> {
            CategoryResponse categoryResponse = map.get(category.getId().toString());
            if(category.getParent() == null)
                categoryResponses.add(categoryResponse);
            else
            {
                map.get(category.getParent().getId().toString()).getChildren().add(categoryResponse);
            }
        });

        return categoryResponses;
    }

    @CacheEvict(value = "categories", key = "'all'")
    @Transactional
    public CategoryAdminResponse addCategory(CategoryRequest categoryRequest)
    {
        Category parent = categoryRequest.getParentId() != null
                ? categoryRepo.findById(UUID.fromString(categoryRequest.getParentId())).orElse(null)
                : null;

        Category category = Category.builder()
                .name(categoryRequest.getName())
                .slug(categoryRequest.getSlug())
                .parent(parent)
                .imageUrl(categoryRequest.getImageUrl())
                .sortOrder(categoryRequest.getSortOrder())
                .build();

        Category save = categoryRepo.save(category);

        return new CategoryAdminResponse(
                save.getId().toString(),
                save.getName(),
                save.getSlug()
        );
    }

    @CacheEvict(value = "categories", key = "'all'")
    @Transactional
    public void updateCategory(UUID id ,CategoryRequest categoryRequest)
    {
        Category category = categoryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Category parent = categoryRequest.getParentId() != null
                ? categoryRepo.findById(UUID.fromString(categoryRequest.getParentId())).orElse(null)
                : null;

        category.setName(categoryRequest.getName());
        category.setSlug(categoryRequest.getSlug());
        category.setParent(parent);
        category.setImageUrl(categoryRequest.getImageUrl());
        category.setSortOrder(categoryRequest.getSortOrder());

        categoryRepo.save(category);
    }

    @Transactional
    @CacheEvict(value = "categories", key = "'all'")
    public void deleteCategory(UUID id)
    {
        if(productRepo.existsByCategory_Id(id))
            throw new CategoryHasProductException("Không thể xóa danh mục đang có sản phẩm.");

        if(categoryRepo.existsByParent_Id(id))
            throw new CategoryHasCategoryChildrenException("Không thể xóa danh mục đang có danh mục con.");

        Category category = categoryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        categoryRepo.delete(category);
    }



    private CategoryResponse toCategoryResponseFromCategory(Category category)
    {
        return new CategoryResponse(
                category.getId().toString(),
                category.getName(),
                category.getSlug(),
                category.getImageUrl(),
                new ArrayList<>()
        );
    }
}
