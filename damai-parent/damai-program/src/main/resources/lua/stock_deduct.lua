-- 库存预扣 Lua 脚本（原子操作）
-- KEYS[1] = stock:{showId}:{ticketTypeId}
-- ARGV[1] = 扣减数量
-- 返回: 1=成功, -1=库存不足, -2=Key不存在

local stock = redis.call('GET', KEYS[1])
if not stock then
    return -2
end
if tonumber(stock) >= tonumber(ARGV[1]) then
    redis.call('DECRBY', KEYS[1], ARGV[1])
    return 1
end
return -1
