package com.czlr.orangemarketbackend.config;

import com.czlr.orangemarketbackend.utils.JwtUtil;
import jakarta.servlet.Filter;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shiro 3.0 安全框架配置类（支持 Jakarta EE）
 * 作用：配置 Shiro 的核心组件和过滤规则
 *
 * 主要功能：
 * 1. 配置 SecurityManager（安全管理器）
 * 2. 配置过滤器链（哪些 URL 需要认证，哪些不需要）
 * 3. 禁用 Session（因为使用 JWT 无状态认证）
 */
@Configuration
public class ShiroConfig {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ShiroRealm shiroRealm;

    /**
     * 构造函数：注入 JWT 工具类和 Redis 模板
     */
    public ShiroConfig(JwtUtil jwtUtil,
                       @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
                       ShiroRealm shiroRealm) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.shiroRealm = shiroRealm;
    }

    /**
     * 配置 Shiro 安全管理器
     * SecurityManager 是 Shiro 的核心，协调所有安全组件
     *
     * 这里配置了无状态模式：
     * - 禁用 Session 存储（因为 JWT 本身就包含了用户信息）
     * - 每次请求都通过 JWT 验证身份，不依赖服务器端 Session
     *
     * @return SecurityManager 实例
     */
    @Bean
    public SecurityManager securityManager() {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(shiroRealm);

        // 配置 Subject DAO（Subject 是 Shiro 中代表用户的对象）
        DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();

        // 创建 Session 存储评估器，并禁用 Session
        DefaultSessionStorageEvaluator sessionStorageEvaluator = new DefaultSessionStorageEvaluator();
        sessionStorageEvaluator.setSessionStorageEnabled(false);  // 禁用 Session 存储

        subjectDAO.setSessionStorageEvaluator(sessionStorageEvaluator);
        securityManager.setSubjectDAO(subjectDAO);

        return securityManager;
    }

    @Bean
    public DefaultAdvisorAutoProxyCreator advisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
        return creator;
    }

    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(
            SecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager);
        return advisor;
    }

    /**
     * 配置 Shiro 过滤器工厂
     * 定义哪些 URL 路径需要认证，哪些可以匿名访问
     *
     * 过滤器链规则：
     * - 规则按顺序匹配，先匹配的先生效
     * - anon：匿名访问，不需要登录
     * - statelessAuth：自定义过滤器，需要 JWT 认证
     *
     * @param securityManager 安全管理器
     * @return ShiroFilterFactoryBean 实例
     */
    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean(SecurityManager securityManager) {
        ShiroFilterFactoryBean shiroFilter = new ShiroFilterFactoryBean();
        shiroFilter.setSecurityManager(securityManager);

        // 注册自定义过滤器（使用 Jakarta EE）
        Map<String, Filter> filters = new LinkedHashMap<>();
        filters.put("statelessAuth", new StatelessAuthFilter(jwtUtil, redisTemplate, securityManager));
        shiroFilter.setFilters(filters);

        // 定义过滤规则（顺序很重要！）
        Map<String, String> filterChainDefinitionMap = new LinkedHashMap<>();

        // 1. 认证相关接口不需要登录（匿名访问）
        filterChainDefinitionMap.put("/api/auth/login", "anon");
        filterChainDefinitionMap.put("/api/auth/sms/send", "anon");
        filterChainDefinitionMap.put("/api/auth/captcha", "anon");

        filterChainDefinitionMap.put("/api/categories", "anon");
        filterChainDefinitionMap.put("/api/products", "anon");
        filterChainDefinitionMap.put("/api/products/**", "anon");

        // 2. 其他所有 /api/** 接口都需要 JWT 认证
        filterChainDefinitionMap.put("/api/**", "statelessAuth");

        shiroFilter.setFilterChainDefinitionMap(filterChainDefinitionMap);

        return shiroFilter;
    }
}
