package com.ice.cartservice.Service;

import com.ice.cartservice.Client.InventoryClient;
import com.ice.cartservice.Client.ProductClient;
import com.ice.cartservice.DTO.Response.Cart.CartItemResponse;
import com.ice.cartservice.DTO.Response.Cart.CartSummaryResponse;
import com.ice.cartservice.DTO.Response.Cart.ListCartItemResponse;
import com.ice.cartservice.DTO.Response.Inventory.ProductBatchItemResponse;
import com.ice.cartservice.DTO.Response.Inventory.ProductBatchResponse;
import com.ice.cartservice.DTO.Response.Inventory.StockBatchResponse;
import com.ice.cartservice.DTO.Response.Inventory.StockItemResponse;
import com.ice.cartservice.Enum.ErrorCode;
import com.ice.cartservice.Enum.StockStatus;
import com.ice.cartservice.Exception.ResourceNotFoundException;
import com.ice.cartservice.Model.Cart;
import com.ice.cartservice.Model.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final InventoryClient inventoryClient;
    private final ProductClient productClient;

    public ListCartItemResponse getCart(String userId)
    {
        String cartId = "cart:" + userId;
        Cart cart = (Cart) redisTemplate.opsForValue().get(cartId);

        if(cart == null)
            throw new ResourceNotFoundException("Giỏ hàng rỗng", ErrorCode.CART_EMPTY);

        List<String> variantIds = new ArrayList<>();
        Map<String, CartItem> cartItemMap = new HashMap<>();

                cart.getItems().values().forEach(
                        cartItem -> {
                            variantIds.add(cartItem.getVariantId());
                            cartItemMap.put(cartItem.getVariantId(), cartItem);
                        }
                );

        StockBatchResponse stockBatchResponse = inventoryClient.getStockBatch(variantIds);

        ProductBatchResponse productBatchResponse = productClient.getProductBatch(variantIds);

        Map<String, StockItemResponse> stockItemMap = new HashMap<>();
        stockBatchResponse.getData().forEach(
                stockItemResponse -> stockItemMap.put(stockItemResponse.getVariantId(), stockItemResponse)
        );


        Integer totalItems = 0;
        Integer totalUniqueItem = 0;
        long subTotal = 0L;
        Boolean hasUnavailableItems = false;

        List<CartItemResponse> cartItemResponses = new ArrayList<>();
        for(ProductBatchItemResponse productBatchItemResponse : productBatchResponse.getVariants())
        {
            String variantId = productBatchItemResponse.getVariantId();
            CartItem cartItem = cartItemMap.get(variantId);
            StockItemResponse stockItemResponse = stockItemMap.get(variantId);
            totalItems += cartItem.getQty();
            long total = productBatchItemResponse.getPrice() * cartItem.getQty();
            subTotal += total;
            totalUniqueItem++;
            if(stockItemResponse.getStatus() == StockStatus.OUT_OF_STOCK)
                hasUnavailableItems = true;

            cartItemResponses.add(new CartItemResponse(
                    cartItem.getCartItemId(),
                    cartItem.getVariantId(),
                    cartItem.getProductId(),
                    productBatchItemResponse.getProductName(),
                    productBatchItemResponse.getProductSlug(),
                    productBatchItemResponse.getThumbnail(),
                    productBatchItemResponse.getColor(),
                    productBatchItemResponse.getSize(),
                    productBatchItemResponse.getSku(),
                    productBatchItemResponse.getPrice(),
                    cartItem.getQty(),
                    total,
                    stockItemResponse.getStatus(),
                    stockItemResponse.getAvailableQty(),
                    stockItemResponse.getStatus() != StockStatus.OUT_OF_STOCK,
                    cartItem.getAddedAt()
            ));
        }

        return new ListCartItemResponse(
                cartId,
                userId,
                cartItemResponses,
                new CartSummaryResponse(
                        totalItems,
                        totalUniqueItem,
                        subTotal,
                        hasUnavailableItems
                ),
                cart.getUpdateAt()
        );
    }
}
