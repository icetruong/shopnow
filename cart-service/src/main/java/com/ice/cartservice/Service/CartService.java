package com.ice.cartservice.Service;

import com.ice.cartservice.DTO.Response.Cart.ListCartItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartService {
    private final RedisTemplate<String, Object> redisTemplate;

    public ListCartItemResponse getCart(Authentication authentication)
    {

    }
}
