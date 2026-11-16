package com.shanyue.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * 【本类知识点：JWT 工具类（jjwt 0.12.6，Jakarta 时代新 API）】
 * 一、JWT 是什么？
 *   一段自包含的字符串，形如 header.payload.signature（Base64 编码）：
 *     - payload 里可放自定义声明（如 empId）和过期时间 exp；
 *     - signature 用服务端密钥对前两段签名 → 客户端改任何内容都会导致验签失败。
 *   服务端无状态：不需要存 session，解析 token 即可知道"是谁、是否过期"。
 *
 * 二、本工程用法：
 *   登录成功 → JwtUtil.createJWT(密钥, 有效期, claims) 生成 token 下发；
 *   携带 token 请求 → 拦截器 JwtUtil.parseJWT(密钥, token) 解出 claims → BaseContext。
 *
 * 三、jjwt 0.9.1 → 0.12.6 的 API 对照（升级 Spring Boot 4 时必改的地方）：
 *   1. 旧版 signWith(SignatureAlgorithm.HS256, byte[]) → 新版 signWith(SecretKey, Jwts.SIG.HS256)：
 *      密钥统一用 Keys.hmacShaKeyFor() 构造成 SecretKey 对象，且强制要求 ≥ 32 字节（256 位）；
 *      本工程 yml 里的两个占位密钥都超过 32 字节，直接可用，自定义时也请保持这个长度。
 *   2. setClaims(claims) → claims(claims)；setExpiration(date) → expiration(date)（builder 风格化）。
 *   3. 解析链整体翻新：Jwts.parser().setSigningKey(byte[]).parseClaimsJws(token).getBody()
 *      → Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()。
 *   4. 旧版在 JDK17 需要 jaxb-api 补丁；0.12.x 彻底移除了对 javax.xml.bind 的依赖，
 *      common 模块的 pom 里已同步删掉 jaxb。
 *
 * 四、注意事项：
 *   1. 密钥只放服务端配置（yml），泄露 = 任何人可伪造 token；
 *   2. 解析抛出的异常（过期 ExpiredJwtException / 验签失败 SignatureException 等）
 *      都继承 io.jsonwebtoken.JwtException（RuntimeException），由拦截器统一 catch。
 */
public class JwtUtil {

    /**
     * 生成 jwt（HS256 对称签名）
     *
     * @param secretKey jwt 秘钥（来自 yml 配置，长度必须 ≥ 32 字节，否则 hmacShaKeyFor 直接抛异常）
     * @param ttlMillis jwt 过期时间（毫秒）
     * @param claims    自定义声明（本工程放 empId / userId）
     * @return token 字符串
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        // 0.12.x：把字符串密钥构造成 SecretKey（内部校验长度 ≥ 256 位）
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        // JWT 的过期时间 = 当前时间 + 有效期
        long expMillis = System.currentTimeMillis() + ttlMillis;

        return Jwts.builder()
                // 设置自定义声明（0.9.1 的 setClaims → 0.12.x 的 claims）
                .claims(claims)
                // 设置过期时间（setExpiration → expiration）
                .expiration(new Date(expMillis))
                // 设置签名算法和密钥（新 API：算法常量 Jwts.SIG.HS256）
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Token 解密（校验）
     *
     * @param secretKey jwt 秘钥：一定要保管好，不能暴露，否则签名可被伪造
     * @param token     加密后的 token（前端放在请求头里）
     * @return 载荷 Claims（含自定义声明与 exp）
     */
    public static Claims parseJWT(String secretKey, String token) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                // 设置签名密钥（0.9.1 的 setSigningKey → 0.12.x 的 verifyWith）
                .verifyWith(key)
                .build()
                // 解析并校验 jwt（签名不符/过期/格式非法都会抛 JwtException 子类）
                .parseSignedClaims(token)
                // 0.9.1 的 getBody → 0.12.x 的 getPayload
                .getPayload();
    }
}
