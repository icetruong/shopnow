package com.ice.productservice.Util;

import com.ice.productservice.Entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class ProductSpecification {
    public static Specification<Product> hasCategoryId(String categoryId)
    {
        return (root, query, cb) ->
                categoryId == null
                ? null
                        : cb.equal(root.get("category").get("id"), UUID.fromString(categoryId));
    }

    public static Specification<Product> hasMinPrice(Long minPrice)
    {
        return (root, query, cb) ->
                minPrice == null
                        ? null
                        : cb.greaterThanOrEqualTo(root.get("basePrice"), minPrice);
    }

    public static Specification<Product> hasMaxPrice(Long maxPrice)
    {
        return (root, query, cb) ->
                maxPrice == null
                        ? null
                        : cb.lessThanOrEqualTo(root.get("basePrice"), maxPrice);
    }

    public static Specification<Product> hasActive(Boolean isActive)
    {
        return (root, query, cb) ->
                isActive == null
                        ? null
                        : cb.equal(root.get("isActive"), isActive);
    }

    public static Specification<Product> isNotDeleted() {
        return (root, query, cb) ->
                cb.equal(root.get("isDelete"), false);
    }

}
