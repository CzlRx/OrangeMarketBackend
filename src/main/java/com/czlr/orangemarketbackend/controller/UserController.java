package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.utils.AuthRedisKey;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户控制器
 * 作用：演示如何在业务接口中使用 JWT 认证后的用户信息
 *
 * 这个 Controller 的所有接口都需要认证（因为路径是 /api/user/**）
 * 请求会先经过 StatelessAuthFilter 验证 JWT token
 * 验证通过后，filter 会将 sessionId 和 userId 注入到 request 中
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 构造函数注入（符合项目规范：构造器注入）
     */
    public UserController(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取当前登录用户的个人资料
     *
     * 使用方式：
     * 1. 前端请求时在 Header 中带上 JWT token：
     *    Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     *
     * 2. StatelessAuthFilter 会自动验证 token，并将用户信息注入到 request
     *
     * 3. 通过 @RequestAttribute 注解可以直接获取：
     *    - sessionId：Redis 中的 session key
     *    - userId：当前登录用户的 ID
     *
     * @param sessionId 由 StatelessAuthFilter 注入，对应 Redis key：auth:login:{userId}{sessionId}
     * @param userId 由 StatelessAuthFilter 注入，当前登录用户的 ID
     * @return 用户信息（从 Redis 读取）
     */
    @RequiresRoles("ADMIN")
    @GetMapping("/profile")
    public Result<Map<Object, Object>> getProfile(
            @RequestAttribute("sessionId") String sessionId,  // 从 request 属性获取 sessionId
            @RequestAttribute("userId") Long userId) {         // 从 request 属性获取 userId

        // 从 Redis 中读取完整的用户信息
        // Redis key 格式：auth:login:{userId}{sessionId}
        // 存储格式：Hash（id, phone, nickname, avatarUrl 等字段）
        String redisKey = AuthRedisKey.login(userId, sessionId);
        Map<Object, Object> userInfo = redisTemplate.opsForHash().entries(redisKey);

        // 返回用户信息给前端
        return Result.success(userInfo);
    }

    /**
     * 其他业务方法示例
     * 任何需要获取当前用户 ID 的方法，都可以用同样的方式获取
     */
    // @GetMapping("/orders")
    // public Result<List<Order>> getMyOrders(@RequestAttribute("userId") Long userId) {
    //     // 使用 userId 查询该用户的订单
    //     List<Order> orders = orderService.getOrdersByUserId(userId);
    //     return Result.success(orders);
    // }
}
