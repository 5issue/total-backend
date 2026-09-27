-- KEYS[1]: product:inventory:{productId}
-- ARGV[1]: 증가시킬 수량
--
-- 캐시가 있을 때만 반영한다. 캐시가 없으면(콜드 캐시) 다음 hold 요청이 DB 값(이미 증가분이
-- 반영된 값)으로 lazy sync하므로 여기서 새로 만들 필요가 없다 — 어설프게 새로 만들면
-- reserved_quantity 없는 반쪽 캐시가 생겨 stock_hold.lua의 캐시 미스 판단을 어지럽힌다.

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

redis.call('HINCRBY', KEYS[1], 'base_quantity', ARGV[1])

return 1
