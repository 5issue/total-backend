-- KEYS[1]: product:inventory:{productId}
-- ARGV[1]: 차감할 수량 (base_quantity, reserved_quantity를 동시에 차감)
--
-- 출고 완료로 실물 재고가 빠질 때 쓴다. 주문 확정 시점에 이미 올라간 reserved_quantity를
-- 함께 내려야 available_quantity(base - reserved)가 그대로 유지된다.
-- 캐시가 있을 때만 반영한다. 콜드 캐시는 다음 hold 요청이 DB 값(이미 차감분 반영됨)으로
-- lazy sync하므로 여기서 새로 만들 필요가 없다.

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

local qty = tonumber(ARGV[1])

local base = redis.call('HGET', KEYS[1], 'base_quantity')
if base then
    redis.call('HSET', KEYS[1], 'base_quantity', math.max(0, tonumber(base) - qty))
end

local reserved = redis.call('HGET', KEYS[1], 'reserved_quantity')
if reserved then
    redis.call('HSET', KEYS[1], 'reserved_quantity', math.max(0, tonumber(reserved) - qty))
end

return 1
