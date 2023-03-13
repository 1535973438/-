package com.dp.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.Follow;
import com.dp.mapper.FollowMapper;
import com.dp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.service.IUserService;
import com.dp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long id = UserHolder.getUser().getId();
        String key = "follows:" + id;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(id);
            follow.setFollowUserId(followUserId);
            boolean save = save(follow);
            if (save)
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
        } else {
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", id).eq("follow_user_id", followUserId));
            if (isSuccess)
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(String followUserId) {
        Long id = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", id).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result getCommon(String id) {
        Long userId = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet()
                .intersect("follows:" + id, "follows:" + userId);
        if (intersect == null) return Result.ok(Collections.emptyList());
        List<Long> ids = intersect.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream().map((user) ->
                BeanUtil.copyProperties(user, UserDTO.class)
        ).collect(Collectors.toList());
        return Result.ok(users);
    }
}
