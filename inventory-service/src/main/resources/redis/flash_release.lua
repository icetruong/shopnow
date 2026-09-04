-- KEYS[1] = flash:stock:{fs}:{variant}
-- KEYS[2] = flash:user:{fs}:{variant}:{userId}
-- KEYS[3] = flash:done:{orderId}:{variant}
-- ARGV[1] = qty

if redis.call('EXISTS', KEYS[3]) == 0 then
    return 0                                               -- chưa reserve / đã release → no-op
end
redis.call('INCRBY', KEYS[1], ARGV[1])
redis.call('DECRBY', KEYS[2], ARGV[1])
redis.call('DEL', KEYS[3])
return tonumber(redis.call('GET', KEYS[1]))