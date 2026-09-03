package com.czlr.orangemarketbackend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 工具类
 * 负责 JWT token 的生成、解析和验证
 *
 * 功能：
 * 1. 生成 JWT token（包含 sessionId、userId、phone）
 * 2. 从 JWT 中提取信息（sessionId、userId）
 * 3. 验证 JWT 的签名和过期时间
 * 4. 获取 JWT 的过期时间
 */
@Component
public class JwtUtil {

    // 从配置文件读取 JWT 密钥，默认值至少 256 位
    @Value("${jwt.secret:orange-market-secret-key-must-be-at-least-256-bits-long-for-hs256}")
    private String secret;

    // JWT 过期时间（秒），默认 86400 秒 = 24 小时
    @Value("${jwt.expiration:86400}")
    private Long expiration;

    /**
     * 获取签名密钥
     * 使用 HMAC-SHA256 算法，密钥必须至少 256 位
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT token
     *
     * @param sessionId Redis 中存储用户信息的 session ID（UUID）
     * @param userId 用户 ID
     * @param phone 用户手机号
     * @return 签名后的 JWT token 字符串
     *
     * JWT 结构：
     * - Header: 算法类型（HS256）
     * - Payload: subject=sessionId, userId, phone, iat（签发时间）, exp（过期时间）
     * - Signature: HMAC-SHA256 签名
     */
    public String generateToken(String sessionId, Long userId, String phone) {
        Instant now = Instant.now();
        Instant expiryDate = now.plusSeconds(expiration);

        return Jwts.builder()
                .subject(sessionId)                      // 主题：sessionId（Redis key 的一部分）
                .claim("userId", userId)                 // 自定义声明：用户 ID
                .claim("phone", phone)                   // 自定义声明：手机号
                .issuedAt(Date.from(now))                // 签发时间
                .expiration(Date.from(expiryDate))       // 过期时间
                .signWith(getSigningKey())               // 使用密钥签名
                .compact();                              // 生成紧凑格式的 JWT 字符串
    }

    /**
     * 从 JWT token 中提取 sessionId
     * sessionId 用于在 Redis 中查找用户信息
     *
     * @param token JWT token 字符串
     * @return sessionId（UUID）
     */
    public String getSessionIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())             // 验证签名
                .build()
                .parseSignedClaims(token)                // 解析 JWT
                .getPayload();                           // 获取负载（Payload）
        return claims.getSubject();                      // 返回 subject 字段（即 sessionId）
    }

    /**
     * 从 JWT token 中提取用户 ID
     *
     * @param token JWT token 字符串
     * @return 用户 ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("userId", Long.class);         // 获取 userId 声明
    }

    /**
     * 验证 JWT token 是否有效
     *
     * 验证内容：
     * 1. 签名是否正确（防止篡改）
     * 2. token 是否过期
     *
     * @param token JWT token 字符串
     * @return true=有效, false=无效或过期
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;                                 // 解析成功，token 有效
        } catch (Exception e) {
            return false;                                // 解析失败，token 无效
        }
    }

    /**
     * 获取 JWT token 的过期时间
     *
     * @param token JWT token 字符串
     * @return 过期时间（Instant）
     */
    public Instant getExpirationFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getExpiration().toInstant();
    }
}
