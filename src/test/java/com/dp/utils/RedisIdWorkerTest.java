package com.dp.utils;

import com.dp.service.impl.ShopServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
@Slf4j
class RedisIdWorkerTest {
    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void Test() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(300);
        Runnable r = () -> {
            for (int i = 0; i < 100; i++) {
                long order = redisIdWorker.nextId("order");
                log.info(String.valueOf(order));
            }
            latch.countDown();
        };
        long s = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(r);
        }
        latch.await();
        long e =System.currentTimeMillis();
        System.out.println(e-s);
    }
}