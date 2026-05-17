package com.ice.productservice.Service;

import com.ice.productservice.Document.ProductDocument;
import com.ice.productservice.Entity.Product;
import com.ice.productservice.Entity.ProductImage;
import com.ice.productservice.Entity.ProductVariant;
import com.ice.productservice.Repository.ProductRepo;
import com.ice.productservice.Repository.ProductSearchRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

// TODO: Tách toàn bộ class này sang Search Service
// Search Service sẽ tự sync vào Elasticsearch sau khi consume Kafka event "product.updated"
// Product Service không nên phụ thuộc vào Elasticsearch
@Service
@RequiredArgsConstructor
public class ProductSyncService {

    private final ProductSearchRepo productSearchRepo;
    private final ProductRepo productRepo;

    public void indexProduct(Product product)
    {
        String thumbnail = product.getProductImages().stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(null);

        List<String> colors = product.getProductVariants().stream()
                .map(ProductVariant::getColor)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<String> sizes = product.getProductVariants().stream()
                .map(ProductVariant::getSize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        ProductDocument doc = ProductDocument.builder()
                .productId(product.getId().toString())
                .name(product.getName())
                .nameSuggest(product.getName())
                .description(product.getDescription())
                .slug(product.getSlug())
                .thumbnail(thumbnail)
                .basePrice(product.getBasePrice())
                .salePrice(product.getSalePrice())
                .rating(product.getRating())
                .soldCount(product.getSoldCount())
                .categoryId(product.getCategory().getId().toString())
                .categoryName(product.getCategory().getName())
                .colors(colors)
                .sizes(sizes)
                .isActive(product.getIsActive())
                .isDelete(product.getIsDelete())
                .createdAt(product.getCreatedAt())
                .build();

        productSearchRepo.save(doc);
    }

    public void deleteProduct(String productId)
    {
        productSearchRepo.deleteById(productId);
    }

    @Transactional(readOnly = true)
    public void syncAll()
    {
        List<Product> products = productRepo.findAll();
        products.forEach(this::indexProduct);
    }
}
