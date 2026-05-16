package com.ice.productservice.Service;

import com.ice.productservice.DTO.Request.Product.ProductRequest;
import com.ice.productservice.DTO.Request.Product.ProductSetIsActiveRequest;
import com.ice.productservice.DTO.Request.Product.ProductUpdateRequest;
import com.ice.productservice.DTO.Response.Product.*;
import com.ice.productservice.Entity.*;
import com.ice.productservice.Exception.ResourceNotFoundException;
import com.ice.productservice.Repository.*;
import com.ice.productservice.Util.ProductSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepo productRepo;
    private final CategoryRepo categoryRepo;
    private final ProductAttributeService productAttributeService;
    private final ProductVariantService productVariantService;


    public PageProductResponse getAllProduct(Integer page, Integer size, String sort, String direction, String categoryId, Long minPrice, Long maxPrice, Boolean isActive) {
        if(minPrice != null && maxPrice != null && minPrice > maxPrice)
            throw new IllegalArgumentException("min price greater than max price");

        Specification<Product> spec = Specification.where(ProductSpecification.isNotDeleted())
                .and(ProductSpecification.hasCategoryId(categoryId))
                .and(ProductSpecification.hasMaxPrice(maxPrice))
                .and(ProductSpecification.hasMinPrice(minPrice))
                .and(ProductSpecification.hasActive(isActive));

        Page<Product> products;

        if(direction.equalsIgnoreCase("ASC"))
            products = productRepo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sort)));
        else
            products = productRepo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort)));


        List<ProductResponse> productResponses = products.stream().map(
                this::toProductResponse
        ).toList();
        return new PageProductResponse(
                productResponses,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages(),
                products.isLast()
        );
    }

    @Cacheable(value = "products", key = "#slug")
    public ProductDetailResponse getProduct(String slug)
    {
        Product product = productRepo.findBySlugAndIsDeleteFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("not found product"));

        return toProductDetailResponse(product);
    }

    @Transactional
    public ProductCreatedResponse createProduct(ProductRequest productRequest)
    {
        Category category = categoryRepo.findById(UUID.fromString(productRequest.getCategoryId()))
                .orElseThrow(() -> new ResourceNotFoundException("category not found"));

        Product product = Product.builder()
                .name(productRequest.getName())
                .slug(productRequest.getSlug())
                .category(category)
                .description(productRequest.getDescription())
                .basePrice(productRequest.getBasePrice())
                .salePrice(productRequest.getSalePrice())
                .isActive(productRequest.getIsActive())
                .build();

        Product save = productRepo.save(product);
        productAttributeService.createProductAttribute(productRequest.getAttributes(), save);
        productVariantService.createProductVariant(productRequest.getVariants(), save);

        return new ProductCreatedResponse(
                save.getId().toString(),
                save.getSlug()
        );
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void updateProduct(UUID id, ProductUpdateRequest productRequest)
    {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("not found product"));

        Category category = categoryRepo.findById(UUID.fromString(productRequest.getCategoryId()))
                .orElseThrow(() -> new ResourceNotFoundException("category not found"));

        product.setName(productRequest.getName());
        product.setSlug(productRequest.getSlug());
        product.setCategory(category);
        product.setDescription(productRequest.getDescription());
        product.setBasePrice(productRequest.getBasePrice());
        product.setSalePrice(productRequest.getSalePrice());
        product.setIsActive(productRequest.getIsActive());

        Product save = productRepo.save(product);
        productAttributeService.deleteAllProductAttributeByProduct(save);
        productAttributeService.createProductAttribute(productRequest.getAttributes(), save);
    }

    @CacheEvict(value = "products", allEntries = true)
    public void setIsActive(UUID id, ProductSetIsActiveRequest request)
    {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("not found product"));

        product.setIsActive(request.getIsActive());
        productRepo.save(product);
    }


    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(UUID id)
    {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("not found product"));

        product.setIsDelete(true);
        productRepo.save(product);
    }

    private ProductDetailResponse toProductDetailResponse(Product product)
    {
        Integer discountPct = product.getSalePrice() == null ? 0 : (int) ((product.getBasePrice() - product.getSalePrice()) * 100 / product.getBasePrice());

        return new ProductDetailResponse(
                product.getId().toString(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getBasePrice(),
                product.getSalePrice(),
                discountPct,
                product.getRating(),
                product.getReviewCount(),
                product.getSoldCount(),
                product.getCategory().getId().toString(),
                product.getCategory().getName(),
                product.getIsActive(),
                product.getProductImages().stream().map(this::toImageProductDetailResponse).toList(),
                product.getProductAttributes().stream().map(this::toAttributeProductDetailResponse).toList(),
                product.getProductVariants().stream().map(this::toVariantProductDetailResponse).toList(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private ImageProductDetailResponse toImageProductDetailResponse(ProductImage productImage)
    {
        return new ImageProductDetailResponse(
            productImage.getId().toString(),
                productImage.getUrl(),
                productImage.getAltText(),
                productImage.getSortOrder(),
                productImage.getIsPrimary()
        );
    }

    private AttributeProductDetailResponse toAttributeProductDetailResponse(ProductAttribute productAttribute)
    {
        return new AttributeProductDetailResponse(
                productAttribute.getName(),
                productAttribute.getValue()
        );
    }

    private VariantProductDetailResponse toVariantProductDetailResponse(ProductVariant productVariant)
    {
        return new VariantProductDetailResponse(
                productVariant.getId().toString(),
                productVariant.getSku(),
                productVariant.getColor(),
                productVariant.getSize(),
                productVariant.getPrice(),
                0,// chưa lấy được từ Inventory Service
                productVariant.getImageUrl()
        );
    }

    private ProductResponse toProductResponse(Product product)
    {
        String thumbnail = product.getProductImages().stream().filter(ProductImage::getIsPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(null);

        Integer discountPct = product.getSalePrice() == null ? 0 : (int) ((product.getBasePrice() - product.getSalePrice()) * 100 / product.getBasePrice());

        return new ProductResponse(
            product.getId().toString(),
                product.getName(),
                product.getSlug(),
                thumbnail,
                product.getBasePrice(),
                product.getSalePrice(),
                discountPct,
                product.getRating(),
                product.getReviewCount(),
                product.getSoldCount(),
                product.getCategory().getId().toString(),
                product.getCategory().getName(),
                product.getIsActive()
        );
    }

}
