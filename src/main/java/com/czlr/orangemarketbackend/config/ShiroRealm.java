package com.czlr.orangemarketbackend.config;

import com.czlr.orangemarketbackend.common.enums.CommonStatus;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import com.czlr.orangemarketbackend.utils.AuthRedisKey;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Component
public class ShiroRealm extends AuthorizingRealm {

    private final UserAccountMapper userAccountMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public ShiroRealm(UserAccountMapper userAccountMapper, RedisTemplate<String, String> redisTemplate) {
        this.userAccountMapper = userAccountMapper;
        this.redisTemplate = redisTemplate;
        setAuthenticationCachingEnabled(false);
        setAuthorizationCachingEnabled(false);
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        return null;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        Long userId = getUserId(principals);
        if (userId == null) {
            return null;
        }

        String sessionId = principals.oneByType(String.class);
        Map<Object, Object> redisUserInfo = getRedisUserInfo(userId, sessionId);
        if (!redisUserInfo.isEmpty()) {
            String status = getValue(redisUserInfo, "status");
            if (status != null && !"active".equalsIgnoreCase(status)) {
                return null;
            }

            String role = getValue(redisUserInfo, "role");
            if ("active".equalsIgnoreCase(status) && role != null && !role.isBlank()) {
                return buildAuthorizationInfo(role);
            }
        }

        UserAccount userAccount = userAccountMapper.selectById(userId);
        if (userAccount == null || userAccount.getStatus() != CommonStatus.ACTIVE) {
            return null;
        }

        return buildAuthorizationInfo(getRoles(userAccount));
    }

    protected Set<String> getRoles(UserAccount userAccount) {
        String role = userAccount.getRole();
        if (role == null || role.isBlank()) {
            return Collections.emptySet();
        }
        return Set.of(role.trim());
    }

    private SimpleAuthorizationInfo buildAuthorizationInfo(Set<String> roles) {
        SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();
        authorizationInfo.setRoles(roles);
        return authorizationInfo;
    }

    private SimpleAuthorizationInfo buildAuthorizationInfo(String role) {
        return buildAuthorizationInfo(Set.of(role.trim()));
    }

    private Map<Object, Object> getRedisUserInfo(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            Map<Object, Object> userInfo = redisTemplate.opsForHash()
                    .entries(AuthRedisKey.login(userId, sessionId));
            return userInfo == null ? Collections.emptyMap() : userInfo;
        } catch (RuntimeException ignored) {
            return Collections.emptyMap();
        }
    }

    private String getValue(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    private Long getUserId(PrincipalCollection principals) {
        if (principals == null || principals.isEmpty()) {
            return null;
        }

        Object principal = principals.getPrimaryPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }
        if (principal instanceof String value) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
