package com.shanyue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 【本类知识点：@ConfigurationProperties 批量绑定配置】
 * 1. prefix = "shanyue.jwt" 表示把 application.yml 里 shanyue.jwt 下的同名配置
 *    按驼峰映射到字段：admin-secret-key → adminSecretKey（松散绑定，自动生效）。
 * 2. @Component 让它成为 Bean，Controller/拦截器可直接 @Autowired 注入。
 * 3. 双端各一套密钥/TTL/token 请求头名：
 *    - 管理端：请求头名 token（对应 yml 的 shanyue.jwt.admin-token-name）
 *    - C 端：请求头名也是 token（对应 shanyue.jwt.user-token-name）——两端密钥不同、
 *      拦截器各管各的路径（/admin/** 与 /user/**），令牌不能混用，安全性靠"密钥隔离"保证
 */
@Component
@ConfigurationProperties(prefix = "shanyue.jwt")
@Data
public class JwtProperties {

    /** 管理端员工生成 jwt 令牌相关配置 */
    private String adminSecretKey;   // jwt 签名密钥
    private long adminTtl;           // 过期时间（毫秒）
    private String adminTokenName;   // 前端传令牌时使用的请求头名

    /** C 端微信用户生成 jwt 令牌相关配置 */
    private String userSecretKey;
    private long userTtl;
    private String userTokenName;
}
