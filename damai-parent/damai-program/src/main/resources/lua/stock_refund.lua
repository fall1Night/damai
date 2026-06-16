-- 库存归还 Lua 脚本（原子操作）
-- KEYS[1] = stock:{showId}:{ticketTypeId}
-- ARGV[1] = 归还数量
-- 返回: 1=成功, -2=Key不存在

local stock = redis.call('GET', KEYS[1])
if not stock then
    return -2
end
redis.call('INCRBY', KEYS[1], ARGV[1])
return 1
