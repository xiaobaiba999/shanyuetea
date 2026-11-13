package com.shanyue.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * 【本类知识点：JWT 工具类（jjwt 0.9.1，课程同款 API）】
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
 * 三、注意事项：
 *   1. 密钥只放服务端配置（yml），泄露 = 任何人可伪造 token；
 *   2. 0.9.1 在 JDK17 上运行需要 jaxb-api 依赖（见父 pom 注释）；
 *   3. 解析抛出的异常（过期 SignatureException/ExpiredJwtException 等）由拦截器统一 catch。
 */
public class JwtUtil {

    /**
     * 生成 jwt
     * 使用 HS256 对称签名算法
     *
     * @param secretKey jwt 秘钥（来自 yml 配置）
     * @param ttlMillis jwt 过期时间（毫秒）
     * @param claims    自定义声明（本工程放 empId / userId）
     * @return token 字符串
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        // 生成 JWT 的过期时间 = 当前时间 + 有效期
        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date exp = new Date(expMillis);

        // 设置 jwt 的 body
        return Jwts.builder()
                // 设置自定义声明
                .setClaims(claims)
                // 设置签名算法和密钥
                .signWith(SignatureAlgorithm.HS256, secretKey.getBytes(StandardCharsets.UTF_8))
                // 设置过期时间
                .setExpiration(exp)
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
        return Jwts.parser()
                // 设置签名密钥（验签用）
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                // 解析并校验 jwt（签名不符/过期都会抛异常）
                .parseClaimsJws(token)
                .getBody();
    }
}
