package com.ice.productservice.Service;

import com.ice.productservice.DTO.Request.Product.VariantProductRequest;
import com.ice.productservice.Entity.Product;
import com.ice.productservice.Entity.ProductVariant;
import com.ice.productservice.Repository.ProductVariantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductVariantService {
    private final ProductVariantRepo productVariantRepo;

    public void createProductVariant(List<VariantProductRequest> requests, Product product)
    {
        List<ProductVariant> productVariants = new ArrayList<>();
        for(VariantProductRequest request : requests)
        {
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
}
