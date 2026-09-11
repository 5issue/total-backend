local idempotencyKey = KEYS[1]
local ttl = tonumber(ARGV[1])
local itemCount = #KEYS - 1

local cachedResult = redis.call('GET', idempotencyKey)
if cachedResult and cachedResult ~= "-1" then
    return tonumber(cachedResult)
end

local inventoryData = {}

for i = 1, itemCount do
    local inventoryKey = KEYS[i + 1]
    local req = tonumber(ARGV[i + 1])

    local inv = redis.call('HMGET', inventoryKey, 'base_quantity', 'reserved_quantity')
    if not inv[1] or not inv[2] then
        return -1 -- 캐시 미스 (재고 정보 없음)
    end

    local base = tonumber(inv[1])
    local reserved = tonumber(inv[2])

    if (base - reserved) < req then
        -- 재고 부족 시, 이번 요청 전체를 실패(0)로 멱등키에 기록하고 즉시 0 반환
        redis.call('SETEX', idempotencyKey, ttl, 0)
        return 0
    end

    inventoryData[i] = {
        key = inventoryKey,
        reserved = reserved,
        req = req
    }
end

for i = 1, itemCount do
    local data = inventoryData[i]
    local newReserved = data.reserved + data.req

    redis.call('HSET', data.key, 'reserved_quantity', newReserved)
end

redis.call('SETEX', idempotencyKey, ttl, 1)
return 1