-- KEYS[1] : 상품 재고 해시 키 (예: product:inventory:1001)
-- ARGV[1] : 취소할 수량

local inventoryKey = KEYS[1]
local idempotencyKey = KEYS[2]
local req = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])


local cachedResult = redis.call('GET', idempotencyKey)

if cachedResult and cachedResult ~= "-1" then
    return tonumber(cachedResult)
end

local reservedVal = redis.call('HGET', inventoryKey, 'reserved_quantity')

-- 예약 정보가 없으면 0 반환
if not reservedVal then
    return -1
end

local reserved = tonumber(reservedVal)

local new_reserved = math.max(0, reserved - req)

redis.call('HSET', KEYS[1], 'reserved_quantity', new_reserved)
redis.call('SETEX', idempotencyKey, ttl, 1)
return 1