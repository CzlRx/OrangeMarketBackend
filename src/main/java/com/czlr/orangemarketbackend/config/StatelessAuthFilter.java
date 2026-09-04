package com.czlr.orangemarketbackend.config;

import com.czlr.orangemarketbackend.utils.JwtUtil;
import com.czlr.orangemarketbackend.utils.AuthRedisKey;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.filter.AccessControlFilter;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 无状态认证过滤器（Shiro 3.0 - 支持 Jakarta EE）
 * 作用：拦截所有需要认证的请求，验证 JWT token 的有效性
 *
 * 工作流程：
 * 1. 从请求头 Authorization 中提取 JWT token
 * 2. 验证 JWT 签名和过期时间
 * 3. 从 JWT 提取 sessionId
 * 4. 检查 Redis 中是否存在对应的 session（用户是否登录）
 * 5. 验证通过后，将 sessionId 和 userId 注入到 request 属性中
 * 6. 验证失败返回 401 状态码
 *
 * 这个过滤器继承自 Shiro 的 AccessControlFilter，
 * 会被 Shiro 框架自动调用，无需手动注册到 Spring
 */
public class StatelessAuthFilter extends AccessControlFilter {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SecurityManager securityManager;

    /**
     * 构造函数：注入 JWT 工具类和 Redis 模板
     */
    public StatelessAuthFilter(JwtUtil jwtUtil,
                               @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
                               SecurityManager securityManager) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.securityManager = securityManager;
    }

    /**
     * 是否允许访问
     * 返回 false 强制所有请求都走 onAccessDenied 方法进行验证
     *
     * @return 始终返回 false，让所有请求都进入认证流程
     */
    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        return false;  // 不允许直接访问，必须经过认证
    }

    /**
     * 访问被拒绝时的处理（实际上是认证入口）
     * 所有需要认证的请求都会进入这个方法
     *
     * @return true=认证通过，继续访问；false=认证失败，返回 401
     */
    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 步骤1：从请求头提取 JWT token
        String token = getTokenFromRequest(httpRequest);

        // 步骤2：检查 token 是否存在
        if (token == null || token.isEmpty()) {
            onLoginFail(httpResponse, "缺少认证 token");
            return false;  // 认证失败，拦截请求
        }

        // 步骤3：验证 JWT 签名和过期时间
        if (!jwtUtil.validateToken(token)) {
            onLoginFail(httpResponse, "token 无效或已过期");
            return false;
        }

        // 步骤4：从 JWT 提取 sessionId
        String sessionId = jwtUtil.getSessionIdFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            onLoginFail(httpResponse, "token 缺少用户信息");
            return false;
        }
        String redisKey = AuthRedisKey.login(userId, sessionId);

        // 步骤5：检查 Redis 中是否存在该 session
        // 即使 JWT 有效，如果用户已登出（Redis 中删除了 session），也应拒绝访问
        Boolean hasKey = redisTemplate.hasKey(redisKey);
        if (hasKey == null || !hasKey) {
            onLoginFail(httpResponse, "会话已过期，请重新登录");
            return false;
        }

        SimplePrincipalCollection principals = new SimplePrincipalCollection(userId, ShiroRealm.class.getName());
        principals.add(sessionId, ShiroRealm.class.getName());
        Subject subject = new Subject.Builder(securityManager)
                .principals(principals)
                .authenticated(true)
                .sessionCreationEnabled(false)
                .buildSubject();
        ThreadContext.bind(subject);

        // 步骤6：认证通过，将用户信息注入到 request 属性
        // 后续的 Controller 可以通过 @RequestAttribute 获取这些信息
        request.setAttribute("sessionId", sessionId);
        request.setAttribute("userId", userId);

        return true;  // 认证通过，放行请求
    }

    /**
     * 从请求头中提取 JWT token
     * 格式：Authorization: Bearer {token}
     *
     * @param request HTTP 请求
     * @return JWT token 字符串，如果不存在返回 null
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        // 检查是否符合 Bearer token 格式
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // 去掉 "Bearer " 前缀
        }
        return null;
    }

    /**
     * 认证失败时的处理
     * 返回 401 状态码和 JSON 格式的错误信息
     *
     * @param response HTTP 响应
     * @param message 错误信息
     */
    private void onLoginFail(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);                                    // HTTP 401 Unauthorized
        response.setContentType("application/json;charset=utf-8");  // JSON 格式
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
