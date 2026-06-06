package com.ice.cartservice.Service;

import com.ice.cartservice.Client.InventoryClient;
import com.ice.cartservice.DTO.Response.Cart.ListCartItemResponse;
import com.ice.cartservice.DTO.Response.Inventory.StockBatchResponse;
import com.ice.cartservice.Enum.ErrorCode;
import com.ice.cartservice.Exception.ResourceNotFoundException;
import com.ice.cartservice.Model.Cart;
import com.ice.cartservice.Model.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final InventoryClient inventoryClient;

    public ListCartItemResponse getCart(String userId)
    {
        String cartId = "cart:" + userId;
        Cart cart = (Cart) redisTemplate.opsForValue().get(cartId);

        if(cart == null)
            throw new ResourceNotFoundException("Giỏ hàng rỗng", ErrorCode.CART_EMPTY);

        List<String> variantIds = cart.getItems().values()
                .stream()
                .map(CartItem::getVariantId)
                .distinct()
                .toList();

        StockBatchResponse stockBatchResponse = inventoryClient.getStockBatch(variantIds);

    }
}
