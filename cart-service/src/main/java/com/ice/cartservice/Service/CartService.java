package com.ice.cartservice.Service;

import com.ice.cartservice.Client.InventoryClient;
import com.ice.cartservice.Client.ProductClient;
import com.ice.cartservice.DTO.Request.Cart.CartItemAddRequest;
import com.ice.cartservice.DTO.Response.Inventory.StockResponse;
import com.ice.cartservice.DTO.Response.Cart.CartItemAddResponse;
import com.ice.cartservice.DTO.Response.Cart.CartItemResponse;
import com.ice.cartservice.DTO.Response.Cart.CartSummaryResponse;
import com.ice.cartservice.DTO.Response.Cart.ListCartItemResponse;
import com.ice.cartservice.DTO.Response.Product.ProductBatchItemResponse;
import com.ice.cartservice.DTO.Response.Product.ProductBatchResponse;
import com.ice.cartservice.DTO.Response.Inventory.StockBatchResponse;
import com.ice.cartservice.DTO.Response.Inventory.StockItemResponse;
import com.ice.cartservice.Enum.ErrorCode;
import com.ice.cartservice.Enum.StockStatus;
import com.ice.cartservice.Exception.ResourceNotFoundException;
import com.ice.cartservice.Exception.StockQuantityException;
import com.ice.cartservice.Model.Cart;
import com.ice.cartservice.Model.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

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

    public CartItemAddResponse addCart(String userId, CartItemAddRequest request)
    {
        List<String> variantIds = List.of(request.getVariantId());
        ProductBatchResponse productBatchResponse = productClient.getProductBatch(variantIds);

        if(productBatchResponse.getVariants().isEmpty() || productBatchResponse.getVariants().getFirst().getIsActive() == false)
            throw new ResourceNotFoundException("Not found product by variantId" + request.getVariantId(), ErrorCode.PRODUCT_UNAVAILABLE);


        StockResponse stockResponse = inventoryClient.getStock(request.getVariantId());

        if(stockResponse.getAvailableQty() == 0)
            throw new StockQuantityException("Sản phẩm này hiện đã hết hàng", ErrorCode.OUT_OF_STOCK);

        if(request.getQty() > stockResponse.getAvailableQty())
            throw new StockQuantityException("Chỉ còn" + stockResponse.getAvailableQty() + "sản phẩm trong kho.", ErrorCode.INSUFFICIENT_STOCK);

        String cartId = "cart:" + userId;
        Cart cart = (Cart) redisTemplate.opsForValue().get(cartId);
        String cartItemId = UUID.randomUUID().toString();
        int totalItem = 0;
        int qty = request.getQty();
        if(cart != null)
        {
            boolean isHas = false;
            for (CartItem cartItem : cart.getItems().values())
            {
                totalItem++;
                if(Objects.equals(cartItem.getVariantId(), request.getVariantId()))
                {
                    qty = cartItem.getQty() + request.getQty();
                    if(qty > stockResponse.getAvailableQty())
                        throw new StockQuantityException("Chỉ còn" + stockResponse.getAvailableQty() + "sản phẩm trong kho.", ErrorCode.INSUFFICIENT_STOCK);

                    isHas = true;
                    cartItem.setQty(qty);
                }
            }
            if(!isHas)
            {
                totalItem++;
                CartItem cartItem = CartItem.builder()
                        .cartItemId(cartItemId)
                        .variantId(stockResponse.getVariantId())
                        .productId(productBatchResponse.getVariants().getFirst().getProductId())
                        .sku(productBatchResponse.getVariants().getFirst().getSku())
                        .qty(request.getQty())
                        .selected(false)
                        .addedAt(Instant.now())
                        .build();
                cart.getItems().put(cartItem.getCartItemId(), cartItem);
            }
            cart.setUpdatedAt(Instant.now());
            redisTemplate.opsForValue().set(cartId, cart, 604800);
        }
        else
        {
            totalItem = 1;
            Map<String, CartItem> map = new HashMap<>();
            CartItem cartItem = CartItem.builder()
                    .cartItemId(cartItemId)
                    .variantId(stockResponse.getVariantId())
                    .productId(productBatchResponse.getVariants().getFirst().getProductId())
                    .sku(productBatchResponse.getVariants().getFirst().getSku())
                    .qty(request.getQty())
                    .selected(false)
                    .addedAt(Instant.now())
                    .build();
            map.put(cartItem.getCartItemId(), cartItem);
            Cart cart2 = Cart.builder()
                    .userId(userId)
                    .updatedAt(Instant.now())
                    .items(map)
                    .build();
            redisTemplate.opsForValue().set(cartId, cart2, 604800);
        }

        return new CartItemAddResponse(
                cartItemId,
                request.getVariantId(),
                qty,
                totalItem
        );
    }
}
