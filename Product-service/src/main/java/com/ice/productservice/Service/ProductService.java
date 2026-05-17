package com.ice.productservice.Service;

import com.ice.productservice.DTO.Request.Internal.ProductRatingInternalRequest;
import com.ice.productservice.DTO.Request.Product.ProductRequest;
import com.ice.productservice.DTO.Request.Product.ProductSetIsActiveRequest;
import com.ice.productservice.DTO.Request.Product.ProductUpdateRequest;
import com.ice.productservice.DTO.Response.Internal.ProductInternalResponse;
import com.ice.productservice.DTO.Response.Internal.ProductRatingInternalResponse;
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
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepo productRepo;
    private final CategoryRepo categoryRepo;
    private final ProductAttributeService productAttributeService;
    private final ProductVariantService productVariantService;
    private final ProductSyncService productSyncService;
    private final RedisTemplate<String, Object> redisTemplate;


    public PageProductResponse getAllProduct(Integer page, Integer size, String sort, String direction, String categoryId, Long minPrice, Long maxPrice, Boolean isActive) {
        String cacheKey = buildCacheKey(page,size, sort,direction,categoryId, minPrice, maxPrice, isActive);

        Object cache = redisTemplate.opsForValue().get(cacheKey);
        if(cache != null)
        {
            return (PageProductResponse) cache;
        }

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

        PageProductResponse response = new PageProductResponse(
                productResponses,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages(),
                products.isLast()
        );

        redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(5));

        return response;
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
        productSyncService.indexProduct(save);

        invalidateListCache();

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
        productSyncService.indexProduct(save);

        invalidateListCache();
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void setIsActive(UUID id, ProductSetIsActiveRequest request)
    {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("not found product"));

        product.setIsActive(request.getIsActive());
        productRepo.save(product);
        productSyncService.indexProduct(product);

        invalidateListCache();
    }


    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(UUID id)
    {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("not found product"));

        product.setIsDelete(true);
        productRepo.save(product);
        productSyncService.deleteProduct(product.getId().toString());

        invalidateListCache();
    }

    public ProductInternalResponse getProductForInternal(UUID productId)
    {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("not found product"));

        return new ProductInternalResponse(
                product.getId().toString(),
                product.getName(),
                product.getIsActive(),
                product.getCategory().getId().toString()
                );
    }

    public ProductRatingInternalResponse updateRating(ProductRatingInternalRequest request)
    {
        Product product = productRepo.findById(UUID.fromString(request.getProductId()))
                .orElse(null);

        if(product == null)
            return new ProductRatingInternalResponse(false);

        product.setRating(request.getAvgRating());
        productRepo.save(product);
        productSyncService.indexProduct(product);

        return new ProductRatingInternalResponse(true);
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

    private String buildCacheKey(Integer page, Integer size, String sort, String direction, String categoryId, Long minPrice, Long maxPrice, Boolean isActive) {
        String params = page + ":" +
                size + ":" +
                sort + ":" +
                direction + ":" +
                categoryId + ":" +
                minPrice + ":" +
                maxPrice + ":" +
                isActive;

        String hash = DigestUtils.md5DigestAsHex(params.getBytes());
        return "products:list:" + hash;
    }

    private void invalidateListCache() {
        ScanOptions options = ScanOptions.scanOptions()
                .match("products:list:*")
                .count(100)
                .build();

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                List<String> keys = new ArrayList<>();
                cursor.forEachRemaining(key -> keys.add(new String(key)));
                if (!keys.isEmpty()) redisTemplate.delete(keys);
            }
            return null;
        });
    }

}
