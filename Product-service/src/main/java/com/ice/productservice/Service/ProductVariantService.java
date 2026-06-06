package com.ice.productservice.Service;

import com.ice.productservice.Client.InventoryClient;
import com.ice.productservice.DTO.Request.Internal.ProductBatchRequest;
import com.ice.productservice.DTO.Request.Internal.StockItemPostRequest;
import com.ice.productservice.DTO.Request.Internal.StockPostRequest;
import com.ice.productservice.DTO.Request.Product.CreateVariantProductRequest;
import com.ice.productservice.DTO.Request.Product.UpdateVariantProductRequest;
import com.ice.productservice.DTO.Request.Product.VariantProductRequest;
import com.ice.productservice.DTO.Response.Internal.ProductBatchResponse;
import com.ice.productservice.DTO.Response.Internal.ProductItemBatchResponse;
import com.ice.productservice.DTO.Response.Internal.ProductVariantInternalResponse;
import com.ice.productservice.DTO.Response.Product.VariantProductResponse;
import com.ice.productservice.Entity.Product;
import com.ice.productservice.Entity.ProductImage;
import com.ice.productservice.Entity.ProductVariant;
import com.ice.productservice.Exception.AlreadyExistsException;
import com.ice.productservice.Exception.ResourceNotFoundException;
import com.ice.productservice.Exception.VariantInActiveOrderException;
import com.ice.productservice.Repository.ProductImageRepo;
import com.ice.productservice.Repository.ProductRepo;
import com.ice.productservice.Repository.ProductVariantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVariantService {
    private final ProductVariantRepo productVariantRepo;
    private final ProductRepo productRepo;
    private final ProductImageRepo productImageRepo;
    private final InventoryClient inventoryClient;

    @Transactional
    public void createProductVariant(List<VariantProductRequest> requests, Product product)
    {
        List<ProductVariant> productVariants = new ArrayList<>();
        List<StockItemPostRequest> items = new ArrayList<>();
        for(VariantProductRequest request : requests)
        {
            if (productVariantRepo.existsBySku(request.getSku()))
                throw new AlreadyExistsException("SKU đã tồn tại.");

            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(request.getSku())
                    .color(request.getColor())
                    .size(request.getSize())
                    .price(request.getPrice())
                    .build();
            productVariants.add(variant);
            items.add(new StockItemPostRequest(
                    variant.getId().toString(),
                    variant.getSku(),
                    request.getStockQty()));
        }
        productVariantRepo.saveAll(productVariants);
        inventoryClient.insertAllStock(new StockPostRequest(items));
    }


    public VariantProductResponse createVariant(UUID productId, CreateVariantProductRequest request)
    {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("not found product"));

        if (productVariantRepo.existsBySku(request.getSku()))
            throw new AlreadyExistsException("SKU đã tồn tại.");


        ProductVariant productVariant = ProductVariant.builder()
                .product(product)
                .sku(request.getSku())
                .color(request.getColor())
                .size(request.getSize())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .build();

        ProductVariant save = productVariantRepo.save(productVariant);

        inventoryClient.insertStock(new StockItemPostRequest(save.getId().toString(), save.getSku(), request.getStockQty()));
        return new VariantProductResponse(save.getId().toString());
    }

    public void updateVariant(UUID id ,UUID productId, UpdateVariantProductRequest request)
    {
        ProductVariant productVariant = productVariantRepo.findByIdAndProduct_Id(id, productId)
                .orElseThrow(() -> new ResourceNotFoundException("not found product variant"));

        productVariant.setPrice(request.getPrice());
        productVariant.setImageUrl(request.getImageUrl());
        productVariantRepo.save(productVariant);
    }

    @Transactional
    public void deleteVariant(UUID id, UUID productId)
    {
        ProductVariant productVariant = productVariantRepo.findByIdAndProduct_Id(id, productId)
                .orElseThrow(() -> new ResourceNotFoundException("not found product variant"));

        // check nếu đơn hàng đang chưa hoàn thành
        if(false)
            throw new VariantInActiveOrderException("Không thể xóa variant đang có trong đơn hàng chưa hoàn thành.");

        productVariantRepo.delete(productVariant);
    }

    public ProductVariantInternalResponse getProductAndVariantForInternal(UUID productId, UUID variantId)
    {
        ProductVariant productVariant = productVariantRepo.findByIdAndProduct_Id(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("not found product variant"));

        return new ProductVariantInternalResponse(
                productVariant.getId().toString(),
                productVariant.getProduct().getId().toString(),
                productVariant.getColor(),
                productVariant.getSku(),
                productVariant.getSize(),
                productVariant.getPrice(),
                productVariant.getIsActive()
        );
    }

    public ProductBatchResponse getProductBatch(ProductBatchRequest request) {
        List<UUID> variantIds = request.getVariantIds().stream()
                .map(UUID::fromString)
                .toList();

        List<ProductVariant> productVariants = productVariantRepo.findAllByIdIn(variantIds);

        // Lấy thumbnail (ảnh primary) của các product trong 1 query để tránh N+1.
        List<UUID> productIds = productVariants.stream()
                .map(variant -> variant.getProduct().getId())
                .distinct()
                .toList();

        Map<UUID, String> primaryThumbnails = productImageRepo
                .findAllByProduct_IdInAndIsPrimaryTrue(productIds).stream()
                .collect(Collectors.toMap(
                        image -> image.getProduct().getId(),
                        ProductImage::getUrl,
                        (existing, replacement) -> existing));

        List<ProductItemBatchResponse> productItemBatchResponses = productVariants.stream()
                .map(variant -> new ProductItemBatchResponse(
                        variant.getId().toString(),
                        variant.getProduct().getId().toString(),
                        variant.getProduct().getName(),
                        variant.getProduct().getSlug(),
                        resolveThumbnail(variant, primaryThumbnails),
                        variant.getSku(),
                        variant.getColor(),
                        variant.getSize(),
                        variant.getPrice(),
                        variant.getIsActive()
                ))
                .toList();

        return new ProductBatchResponse(productItemBatchResponses);
    }

    // Ưu tiên ảnh riêng của variant; nếu không có thì fallback về thumbnail của product.
    private String resolveThumbnail(ProductVariant variant, Map<UUID, String> primaryThumbnails) {
        if (variant.getImageUrl() != null) {
            return variant.getImageUrl();
        }
        return primaryThumbnails.get(variant.getProduct().getId());
    }
}
