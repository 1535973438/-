package com.dp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIME=1676734586L;
    private static final int COUNT_BITS=32;
    public long nextId(String idPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        //生成序列号
        long timestamp=nowSecond-BEGIN_TIME;
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + idPrefix + ":" + date);
        //拼接
        return timestamp<<COUNT_BITS|count;
    }


}
