local idempotencyKey = KEYS[1]
local ttl = tonumber(ARGV[1])
local itemCount = #KEYS - 1


local cachedResult = redis.call('GET', idempotencyKey)
if cachedResult and cachedResult ~= "-1" then
    return tonumber(cachedResult)
end

local releaseData = {}

for i = 1, itemCount do
    local inventoryKey = KEYS[i+1]
    local req = tonumber(ARGV[i+1])

    local reservedVal = redis.call('HGET', inventoryKey, 'reserved_quantity')
    if reservedVal then
        local reserved = tonumber(reservedVal)
        local newReserved = math.max(0, reserved - req)

        releaseData[i] = {
            key = inventoryKey,
            newReserved = newReserved,
            exist = true
        }
    else
        releaseData[i] = {
            exist = false
        }
    end
end

for i = 1, itemCount do
    local data = releaseData[i]
    if data.exist then
        redis.call('HSET', data.key, 'reserved_quantity', data.newReserved)
    end
end

redis.call('SETEX', idempotencyKey, ttl, 1)
return 1