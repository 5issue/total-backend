-- KEYS[1]: product:inventory:{productId}
-- ARGV[1]: base_quantity
-- ARGV[2]: reserved_quantity
--
-- 캐시가 이미 있으면 덮어쓰지 않는다. 다른 요청이 이미 선점한 reserved_quantity를
-- DB 값(확정분만 반영)으로 되돌리면 초과 판매가 난다.
-- 존재 확인과 쓰기를 한 스크립트에서 처리해 그 사이에 다른 요청이 끼어들 수 없게 한다.

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0 -- 이미 캐시가 있음 (다른 요청이 먼저 채웠다)
end

redis.call('HSET', KEYS[1], 'base_quantity', ARGV[1], 'reserved_quantity', ARGV[2])

return 1
