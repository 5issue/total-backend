-- KEYS[1]: reservation:{reservationToken}
-- ARGV[1]: 확정 후 토큰을 보관할 TTL(초)
--
-- HOLD 상태인 예약만 CONFIRM으로 전이한다. 이 전이가 있어야 stock_release.lua가 뒤늦게
-- 도착한 해제 이벤트로부터 이미 확정된 예약을 보호할 수 있다(release는 HOLD만 취소한다).

local reservationTokenKey = KEYS[1]
local ttl = tonumber(ARGV[1])

local status = redis.call('HGET', reservationTokenKey, 'status')

if status == 'CONFIRM' then
    return 1 -- 이미 확정됨 (멱등 처리)
end

if status ~= 'HOLD' then
    return -1 -- HOLD 상태가 아니면 확정할 수 없다 (토큰이 없거나 이미 RELEASE됨)
end

redis.call('HSET', reservationTokenKey, 'status', 'CONFIRM')
redis.call('EXPIRE', reservationTokenKey, ttl)

return 1
