--参数列表
local voucherId=ARGV[1]
local userId=ARGV[2]
local orderId=ARGV[3]

local stockKey='seckill:stock:' .. voucherId
local orderKey='seckill:order:' .. voucherId

--脚本业务
--判断库存是否充足
if(tonumber(redis.call('get',stockKey))<=0) then
    --库存不足，返回1
    return 1
end

--判断当前用户是否下单
if(redis.call("sismember",orderKey,userId)== 1)  then
    --存在，重复下单返回2
    return 2
end

--扣库存
redis.call('incrby',stockKey,-1)
--创建订单
redis.call("sadd",orderKey,userId)

redis.call("xadd",'stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0