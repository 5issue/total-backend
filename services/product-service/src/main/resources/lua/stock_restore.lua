-- KEYS[1..N]: product:inventory:{productId} 상품 재고 캐시 키들
-- ARGV[1..N]: 각 키에 대응해서 되돌릴 수량
--
-- DB 쪽 확정 수량을 되돌릴 때(restore) Redis의 reserved_quantity도 같이 되돌린다.
-- 예약 토큰 상태와는 무관한 순수 상품별 카운터 조정이라 reservation:{token} 키는 필요 없다.
-- 한 항목씩 즉시 읽고 쓰기 때문에(stock_release.lua와 동일한 패턴) 같은 productId가
-- 중복으로 들어와도 누적 반영된다. 캐시가 없는 상품은 조용히 건너뛴다.

local itemCount = #KEYS

for i = 1, itemCount do
    local inventoryKey = KEYS[i]
    local qty = tonumber(ARGV[i])

    local reservedVal = redis.call('HGET', inventoryKey, 'reserved_quantity')
    if reservedVal then
        local reserved = tonumber(reservedVal)
        local newReserved = math.max(0, reserved - qty)
        redis.call('HSET', inventoryKey, 'reserved_quantity', newReserved)
    end
end

return 1
