-- KEYS[1] : 상품 재고 해시 키 (예: product:inventory:1001)
-- ARGV[1] : 요청 수량 (주문 수량)

local inventoryKey = KEYS[1]
local idempotencyKey = KEYS[2]
local req = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

local cachedResult = redis.call('GET', idempotencyKey)
if cachedResult and cachedResult ~= "-1" then
    return tonumber(cachedResult)
end

 -- 이미 처리된 요청이면 기존에 응답했던 결과 반환 (1: 성공, 0: 재고부족, -1: 재고정보없음 등)
local inv = redis.call('HMGET', inventoryKey, 'base_quantity', 'reserved_quantity')

-- 재고 정보가 존재하지 않는 경우 예외 코드 반환 (-1)
if not inv[1] or not inv[2] then
    return -1 -- 재고 정보 없음
end

local base = tonumber(inv[1])
local reserved = tonumber(inv[2])

-- 가용 재고 계산 및 부족 여부 확인
if (base - reserved) < req then
    redis.call('SETEX', idempotencyKey, ttl, 0)
    return 0 -- 재고 부족
end

-- 예약 수량(reserved_quantity) 증가 후 성공 코드 반환 (1)
redis.call('HSET', inventoryKey, 'reserved_quantity', reserved + req)

redis.call('SETEX', idempotencyKey, ttl, 1)
return 1 -- 재고 선점 성공