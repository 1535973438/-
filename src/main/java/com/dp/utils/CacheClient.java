package com.dp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import static com.dp.utils.RedisConstants.*;


@Slf4j
@Component
public class CacheClient {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public void set(String key , Object val, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(val),time,unit);
    }
    public void setWithLogicalExpire(String key , Object val, Long time,TimeUnit unit){
        RedisData redisData=new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(val);
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
    /**
     * 互斥锁解决缓存击穿
     * @return
     */
    public  <T,ID> T queryWithMutex(
            String cachePrefix, String lockPrefix, ID id, Class<T> type, Function<ID,T> dbFallback,Long time,TimeUnit unit) {
        //从redis查缓存、
        String Json = stringRedisTemplate.opsForValue().get(cachePrefix + id);
        //        命中直接返回
        if (StrUtil.isNotBlank(Json)) {
            return JSONUtil.toBean(Json, type);
        }
        if (Json != null) {
            return null;
        }
        String key = lockPrefix + id;
        T t;
        try {
            if (!trtLock(key)) {
                return queryWithMutex(cachePrefix, lockPrefix, id, type, dbFallback, time, unit);
            }
//        查数据库
            t = dbFallback.apply(id);
//        不存在报错
            if (t == null) {
                set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
//        储存到缓存并返回数据
            set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(t), time, unit);
        } finally {
            unLock(key);
        }

        return t;
    }
    public  <T,ID> T queryWithLogicalExpire( String cachePrefix, String lockPrefix, ID id, Class<T> type, Function<ID,T> dbFallback,Long time,TimeUnit unit) {
        //从redis查缓存、
        String Json = stringRedisTemplate.opsForValue().get(cachePrefix + id);
        //未命中直接返回
        if (StrUtil.isBlank(Json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        T t = JSONUtil.toBean((JSONObject)redisData.getData(), type);
        //未过期直接返回
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return t;
        }
        //过期重建缓存
        String key = lockPrefix + id;
        //获取失败直接返回数据
        if(!trtLock(key)){
            return t;
        }
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try {
                T apply = dbFallback.apply(id);
                setWithLogicalExpire(cachePrefix + id,apply,time,unit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                unLock(key);
            }
        });
        return t;
    }


    private boolean trtLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
