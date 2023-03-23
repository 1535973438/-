package com.dp.service;

import com.dp.dto.Result;
import com.dp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zzx
 * @since 2022-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(String followUserId);

    Result getCommon(String id);
}
