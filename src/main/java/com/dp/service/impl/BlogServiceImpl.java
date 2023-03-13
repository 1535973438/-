package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.Result;
import com.dp.dto.ScrollResult;
import com.dp.dto.UserDTO;
import com.dp.entity.Blog;
import com.dp.entity.Follow;
import com.dp.entity.User;
import com.dp.mapper.BlogMapper;
import com.dp.service.IBlogService;
import com.dp.service.IFollowService;
import com.dp.service.IUserService;
import com.dp.utils.SystemConstants;
import com.dp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.dp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.dp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;
    @Override
    public Result queryBolgById(String id) {
        Blog blog = getById(id);
        if (blog == null) return Result.fail("博客不存在");
        queryBolgUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach((blog) -> {
                    isBlogLiked(blog);
                    queryBolgUser(blog);
                }
        );
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取用户的id
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        //查看是否点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 修改点赞数量
        if (score == null) {
            boolean res = update().setSql("liked = liked + 1").eq("id", id).update();
            if (res) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            boolean res = update().setSql("liked = liked - 1").eq("id", id).update();
            if (res) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("order by field(id," + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if(!save){
            return  Result.fail("新增笔记失败");
        }
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key=FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if(typedTuples==null||typedTuples.isEmpty()) {
            return Result.ok();
        }
        int os=1;
        long minTime=0;
        List<Long> ids=new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));
            long time= Objects.requireNonNull(typedTuple.getScore()).longValue();
            if(time==minTime){
                os++;
            }else{
                minTime=time;
                os=1;
            }
        }
        String idStr=StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id", ids).last("")
                .last("order by field(id," + idStr + ")").list();
        for (Blog blog : blogs) {
            isBlogLiked(blog);
            queryBolgUser(blog);
        }
        ScrollResult scrollResult=new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }

    private void queryBolgUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        Long userId = blog.getUserId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
