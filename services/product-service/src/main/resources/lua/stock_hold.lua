local reservationTokenKey = KEYS[1]
local itemCount = #KEYS - 1

local currentStatus = redis.call('HGET', reservationTokenKey, 'status')
if currentStatus == 'HOLD' then
    return 1
end

local inventoryData = {}
local itemsToStore = {}

for i = 1, itemCount do
    local inventoryKey = KEYS[1 + i]              -- 상품 재고 캐시 키 (KEYS[2], KEYS[3]...)
    local productId = ARGV[2 * i - 1]             -- 상품 ID (ARGV[1], ARGV[3]...)
    local req = tonumber(ARGV[2 * i])             -- 요청 수량 (ARGV[2], ARGV[4]...)

    local inv = redis.call('HMGET', inventoryKey, 'base_quantity', 'reserved_quantity')
    if not inv[1] or not inv[2] then
        return -1 -- 캐시 미스 (재고 정보 없음)
    end

    local base = tonumber(inv[1])
    local reserved = tonumber(inv[2])

    if (base - reserved) < req then
        return 0 -- 재고 부족, 전체 실패
    end

    inventoryData[i] = {
        key = inventoryKey,
        reserved = reserved,
        req = req
    }

    itemsToStore[i] = productId .. ":" .. req
end

-- 재고 차감 (reserved_quantity 증가)
for i = 1, itemCount do
    local data = inventoryData[i]
    local newReserved = data.reserved + data.req
    redis.call('HSET', data.key, 'reserved_quantity', newReserved)
end

-- reservationToken 키에 상태·아이템·생성 시각 저장(HOLD 상태)
redis.call('HSET', reservationTokenKey, 'status', 'HOLD')
redis.call('HSET', reservationTokenKey, 'items', table.concat(itemsToStore, ","))
redis.call('HSET', reservationTokenKey, 'created_at', redis.call('TIME')[1])

return 1
