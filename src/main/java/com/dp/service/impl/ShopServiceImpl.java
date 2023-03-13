package com.dp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.dto.Result;
import com.dp.entity.Shop;
import com.dp.mapper.ShopMapper;
import com.dp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.utils.CacheClient;
import com.dp.utils.RedisData;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.dp.utils.RedisConstants.*;
import static com.dp.utils.SystemConstants.DEFAULT_PAGE_SIZE;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    /**
     * 查询商户详细信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
         Shop shop = cacheClient.queryWithMutex(
                 CACHE_SHOP_KEY, LOCK_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        Shop shop = cacheClient.queryWithLogicalExpire(
//                CACHE_SHOP_KEY, LOCK_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null)
            return Result.fail("店铺不存在");
        return Result.ok(shop);
    }


    public void saveShop2Redis(Long id, Long expireSecond) throws InterruptedException {
        //查询
        Shop shop = getById(id);
        Thread.sleep(200);
        //封装成逻辑过期
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
        redisData.setData(shop);
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null)
            return Result.fail("店铺id不能为空");
        this.updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            Result.ok(page.getRecords());
        }
        //计算分页参数
        int from = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;
        //查询redis、按照距离排序，分页，结果：shopId，distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from)
            //没有下一页了，结束
            return Result.ok(Collections.emptyList());
        //截取from~end部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(
                //获取店铺id
                result -> {
                    String shopIdStr = result.getContent().getName();
                    ids.add(Long.valueOf(shopIdStr));
                    Distance distance = result.getDistance();
                    distanceMap.put(shopIdStr, distance);
                }
        );
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
