package com.dp;

import cn.hutool.json.JSONUtil;
import com.dp.entity.Shop;
import com.dp.entity.ShopType;
import com.dp.service.IShopService;
import com.dp.service.IShopTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.dp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    IShopTypeService typeService;
    @Resource
    IShopService shopService;
    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    public void loadShopData() {
        List<Shop> shops = shopService.list();
        Map<Long, List<Shop>> collects = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : collects.entrySet()) {
            Long typeId=entry.getKey();
            String key=SHOP_GEO_KEY+typeId;
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>(value.size());
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            redisTemplate.opsForGeo().add(key,locations);
        }
    }
//    @Test
//    public void loadShop(){
//        List<ShopType> typeList = typeService
//                .query().orderByAsc("sort").list();
//        List<String> collect = typeList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
//        for (String s : collect) {
//            redisTemplate.opsForList().rightPush("shop-type",s);
//        }
//    }
}
