package com.dp.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class ShopServiceTest {
    @Autowired
    private ShopServiceImpl shopService;
    @Test
    public void  test() throws InterruptedException {
        shopService.saveShop2Redis(1L,10L);
    }
}