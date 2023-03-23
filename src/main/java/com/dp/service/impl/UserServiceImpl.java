package com.dp.service.impl;

import java.time.LocalDateTime;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.LoginFormDTO;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.User;
import com.dp.mapper.UserMapper;
import com.dp.service.IUserService;
import com.dp.utils.RegexUtils;
import com.dp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.dp.utils.RedisConstants.*;
import static com.dp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author zzx
 * @since 2022-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone))
            return Result.fail("手机号错误");
        String code = RandomUtil.randomNumbers(6);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送短信验证码成功:" + code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if (RegexUtils.isPhoneInvalid(phone))
            return Result.fail("手机号格式错误");
        if (RegexUtils.isCodeInvalid(code))
            return Result.fail("验证码格式错误");

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        User user = this.query().eq("phone", phone).one();
        if (user == null) {
            user = creatUserWithPhone(phone);
        }
        //保存用户信息到redis中
        //生成token，作为登陆令牌
        String token = UUID.randomUUID().toString();
        //将user对象转为hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );
        //储存
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //设置有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);
        //返回token
        if (user == null) {
            return Result.fail("注册失败，请稍后再试");
        }
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long id = UserHolder.getUser().getId();
        //获取今天日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + id + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long id = UserHolder.getUser().getId();
        //获取今天日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + id + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        List<Long> res = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(res==null||res.isEmpty())
            return Result.ok(0);
        Long num = res.get(0);
        if(num==null||num==0)
            return Result.ok(0);
        int count=1;
        while(true){
            if((num&1)==0){
                break;
            }else{
                count++;
            }
            num=num>>1;
        }
        return Result.ok(count);
    }


    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        boolean save = save(user);
        if (save)
            return user;
        return null;

    }
}
