package com.ice.productservice.Service;

import com.ice.productservice.DTO.Request.Product.AttributeProductRequest;
import com.ice.productservice.Entity.Product;
import com.ice.productservice.Entity.ProductAttribute;
import com.ice.productservice.Exception.ResourceNotFoundException;
import com.ice.productservice.Repository.ProductAttributeRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAttributeService {
    private final ProductAttributeRepo productAttributeRepo;

    public void createProductAttribute(List<AttributeProductRequest> requests, Product product)
    {
        List<ProductAttribute> productAttributes = new ArrayList<>();
        for(int i = 0;i<requests.size(); i++)
        {
            AttributeProductRequest attributeProductRequest = requests.get(i);
            productAttributes.add(ProductAttribute.builder()
                    .product(product)
                    .name(attributeProductRequest.getName())
                    .value(attributeProductRequest.getValue())
                    .sortOrder(i)
                    .build());
        }

        productAttributeRepo.saveAll(productAttributes);
    }

    public void deleteAllProductAttributeByProduct(Product product)
    {
        productAttributeRepo.deleteAllByProduct(product);
    }
}
