package com.dp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock {
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    private final String KET_PREFIX = "lock:";
    private final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean tryLock(long timeoutSec) {
        String threadID = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KET_PREFIX + name, threadID, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public void unlock() {
        String threadID = ID_PREFIX + Thread.currentThread().getId();
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KET_PREFIX + name), threadID);
    }

//    public void unlock() {
//        String threadID = ID_PREFIX + Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KET_PREFIX + name);
//        if (threadID.equals(id))
//            stringRedisTemplate.delete(KET_PREFIX + name);
//    }
}
