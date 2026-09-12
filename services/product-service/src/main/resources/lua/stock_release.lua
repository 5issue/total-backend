local reservationTokenKey = KEYS[1]  -- KEYS[1]: reservation:reservationToken (예: "reservation:rsv_8f7b...")
local releaseTtl = tonumber(ARGV[1])   -- 취소 후 토큰을 보관할 TTL (예: 1시간 또는 하루)

-- 1. 토큰 존재 여부 확인
local status = redis.call('HGET', reservationTokenKey, 'status')
if not status then
    return -1 -- 존재하지 않는 토큰 (잘못된 요청)
end

if status == 'RELEASE' then
    return 1  -- 이미 취소된 경우 (멱등성 성공 처리)
end

local itemsStr = redis.call('HGET', reservationTokenKey, 'items')
if not itemsStr or itemsStr == "" then
    -- 아이템 정보가 없어도 상태는 RELEASE로 변경
    redis.call('HSET', reservationTokenKey, 'status', 'RELEASE')
    redis.call('EXPIRE', reservationTokenKey, releaseTtl)
    return 1
end

-- 2. 저장된 아이템 파싱 및 재고 복구 (reserved_quantity 감소)
-- itemsStr 형식: "productId:quantity,productId:quantity"
for item in string.gmatch(itemsStr, "([^,]+)") do
    local productId, quantity = item:match("([^:]+):([^:]+)")
    local inventoryKey = "product:inventory:" .. productId
    local qty = tonumber(quantity)

    local reservedVal = redis.call('HGET', inventoryKey, 'reserved_quantity')
    if reservedVal then
        local reserved = tonumber(reservedVal)
        local newReserved = math.max(0, reserved - qty)
        redis.call('HSET', inventoryKey, 'reserved_quantity', newReserved)
    end
end

-- 3. 토큰 상태를 RELEASE로 변경하고 TTL 설정 (시간이 지나면 자동 삭제)
redis.call('HSET', reservationTokenKey, 'status', 'RELEASE')
redis.call('EXPIRE', reservationTokenKey, releaseTtl)

return 1
