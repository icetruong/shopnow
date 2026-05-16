package com.ice.productservice.Service;

import com.ice.productservice.DTO.Request.Product.CreateVariantProductRequest;
import com.ice.productservice.DTO.Request.Product.UpdateVariantProductRequest;
import com.ice.productservice.DTO.Request.Product.VariantProductRequest;
import com.ice.productservice.DTO.Response.Product.VariantProductResponse;
import com.ice.productservice.Entity.Product;
import com.ice.productservice.Entity.ProductVariant;
import com.ice.productservice.Exception.ResourceNotFoundException;
import com.ice.productservice.Exception.VariantInActiveOrderException;
import com.ice.productservice.Repository.ProductRepo;
import com.ice.productservice.Repository.ProductVariantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductVariantService {
    private final ProductVariantRepo productVariantRepo;
    private final ProductRepo productRepo;

    public void createProductVariant(List<VariantProductRequest> requests, Product product)
    {
        List<ProductVariant> productVariants = new ArrayList<>();
        for(VariantProductRequest request : requests)
        {
            // request.getStockQty() không được gọi ở đây → vì sẽ cho qua inventory service
            productVariants.add(ProductVariant.builder()
                    .product(product)
                    .sku(request.getSku())
                    .color(request.getColor())
                    .size(request.getSize())
                    .price(request.getPrice())
                    .build());
        }
        productVariantRepo.saveAll(productVariants);
    }


    public VariantProductResponse createVariant(UUID productId, CreateVariantProductRequest request)
    {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("not found product"));
        // request.getStockQty() không được gọi ở đây → vì sẽ cho qua inventory service
        ProductVariant productVariant = ProductVariant.builder()
                .product(product)
                .sku(request.getSku())
                .color(request.getColor())
                .size(request.getSize())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .build();

        ProductVariant save = productVariantRepo.save(productVariant);

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
}
