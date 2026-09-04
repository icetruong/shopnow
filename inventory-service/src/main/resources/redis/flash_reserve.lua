-- KEYS[1] = flash:stock:{flashSaleId}:{variantId}      (counter tồn kho)
-- KEYS[2] = flash:user:{flashSaleId}:{variantId}:{userId}  (counter user đã mua)
-- KEYS[3] = flash:active:{flashSaleId}                  (cờ đang chạy)
-- ARGV[1] = qty
-- ARGV[2] = limitPerUser
-- ARGV[3] = ttl giây cho key user (số giây còn lại tới endsAt)

if redis.call('EXISTS', KEYS[3]) == 0 then
    return -3                                              -- flash sale không active
end

local userBought = tonumber(redis.call('GET', KEYS[2]) or '0')
if userBought + tonumber(ARGV[1]) > tonumber(ARGV[2]) then
    return -2                                              -- vượt limitPerUser
end

local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock < tonumber(ARGV[1]) then
    return -1                                              -- hết hàng
end

redis.call('DECRBY', KEYS[1], ARGV[1])
redis.call('INCRBY', KEYS[2], ARGV[1])
redis.call('EXPIRE', KEYS[2], ARGV[3])

return tonumber(redis.call('GET', KEYS[1]))