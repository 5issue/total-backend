-- KEYS[1] : 상품 재고 해시 키 (예: product:inventory:1001)
-- ARGV[1] : 취소할 수량

local inventoryKey = KEYS[1]
local releaseIdempotencyKey = KEYS[2]
local holdIdempotencyKey = KEYS[3]
local req = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])


local cachedResult = redis.call('GET', releaseIdempotencyKey)

if cachedResult and cachedResult ~= "-1" then
    return tonumber(cachedResult)
end

-- 2. 실제 선점(Hold) 이력이 존재하는지 검증
local holdRecord = redis.call('GET', holdIdempotencyKey)
if not holdRecord or holdRecord ~= "1" then
    -- 선점된 적이 없거나 이미 만료/취소된 건인데 해제가 들어온 경우 거부 (-2)
    redis.call('SETEX', releaseIdempotencyKey, ttl, 1)
    return -2
end

local reservedVal = redis.call('HGET', inventoryKey, 'reserved_quantity')

-- 예약 정보가 없으면 0 반환
if not reservedVal then
    return -1
end

local reserved = tonumber(reservedVal)

local new_reserved = math.max(0, reserved - req)

redis.call('HSET', inventoryKey, 'reserved_quantity', new_reserved)
redis.call('SETEX', releaseIdempotencyKey, ttl, 1)
redis.call('DEL', holdIdempotencyKey)
return 1 -- 재고 해제 성공