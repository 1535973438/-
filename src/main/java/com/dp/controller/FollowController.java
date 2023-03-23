package com.dp.controller;


import com.dp.dto.Result;
import com.dp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author zzx
 * @since 2022-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") String followUserId) {
        return followService.isFollow(followUserId);
    }
    @GetMapping("/common/{id}")
    public Result getCommon(@PathVariable String id){
        return followService.getCommon(id);
    }
}
