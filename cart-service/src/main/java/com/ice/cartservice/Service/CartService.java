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
import com.ice.cartservice.Enum.StockStatus;
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

        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return new ListCartItemResponse(
                    cartId,
                    userId,
                    List.of(),                                   // items rỗng
                    new CartSummaryResponse(0, 0, 0L, false),    // summary 0
                    null                                         // updatedAt
            );
        }
        List<String> variantIds = cart.getItems().values().stream()
                .map(CartItem::getVariantId)
                .toList();

        StockBatchResponse stockBatchResponse = inventoryClient.getStockBatch(variantIds);
        ProductBatchResponse productBatchResponse = productClient.getProductBatch(variantIds);

        Map<String, StockItemResponse> stockMap = new HashMap<>();
        stockBatchResponse.getData().forEach(
                stock -> stockMap.put(stock.getVariantId(), stock)
        );

        Map<String, ProductBatchItemResponse> productMap = new HashMap<>();
        productBatchResponse.getVariants().forEach(
                product -> productMap.put(product.getVariantId(), product)
        );

        int totalItems = 0;
        int totalUniqueItems = 0;
        long subTotal = 0L;
        boolean hasUnavailableItems = false;

        List<CartItemResponse> cartItemResponses = new ArrayList<>();

        // Duyệt theo cart (nguồn sự thật), KHÔNG theo product response → item bị xoá vẫn hiện.
        for (CartItem cartItem : cart.getItems().values())
        {
            String variantId = cartItem.getVariantId();
            ProductBatchItemResponse product = productMap.get(variantId);
            StockItemResponse stock = stockMap.get(variantId);

            totalUniqueItems++;
            totalItems += cartItem.getQty();

            // Product bị xoá khỏi catalog → đánh dấu không khả dụng, không giấu đi.
            if (product == null)
            {
                hasUnavailableItems = true;
                cartItemResponses.add(new CartItemResponse(
                        cartItem.getCartItemId(),
                        variantId,
                        cartItem.getProductId(),
                        null, null, null, null, null,   // name, slug, thumbnail, color, size
                        cartItem.getSku(),
                        0L,                              // unitPrice (Redis không lưu giá)
                        cartItem.getQty(),
                        0L,                              // subtotal
                        StockStatus.OUT_OF_STOCK,
                        0,                               // availableQty
                        false,                           // isAvailable
                        cartItem.getAddedAt()
                ));
                continue;
            }

            // Stock có thể thiếu → mặc định an toàn là hết hàng.
            StockStatus status   = (stock != null) ? stock.getStatus()       : StockStatus.OUT_OF_STOCK;
            Integer availableQty = (stock != null) ? stock.getAvailableQty() : 0;

            // Khả dụng = product còn active VÀ còn hàng.
            boolean isAvailable = Boolean.TRUE.equals(product.getIsActive())
                    && status != StockStatus.OUT_OF_STOCK;
            if (!isAvailable) hasUnavailableItems = true;

            long lineTotal = product.getPrice() * cartItem.getQty();
            subTotal += lineTotal;

            cartItemResponses.add(new CartItemResponse(
                    cartItem.getCartItemId(),
                    variantId,
                    cartItem.getProductId(),
                    product.getProductName(),
                    product.getProductSlug(),
                    product.getThumbnail(),
                    product.getColor(),
                    product.getSize(),
                    product.getSku(),
                    product.getPrice(),
                    cartItem.getQty(),
                    lineTotal,
                    status,
                    availableQty,
                    isAvailable,
                    cartItem.getAddedAt()
            ));
        }

        return new ListCartItemResponse(
                cartId,
                userId,
                cartItemResponses,
                new CartSummaryResponse(
                        totalItems,
                        totalUniqueItems,
                        subTotal,
                        hasUnavailableItems
                ),
                cart.getUpdatedAt()
        );
    }
}
