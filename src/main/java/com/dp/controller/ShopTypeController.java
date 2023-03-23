package com.dp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.ShopType;
import com.dp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author zzx
 * @since 2022-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {

//        List<String> range = redisTemplate.opsForList().range("shop-type", 0, -1);
//        List<ShopType> typeList = range.stream().map(
//                a -> JSONUtil.toBean(a, ShopType.class)).collect(Collectors.toList());
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
