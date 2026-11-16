package com.shanyue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 【本类知识点：启动类注解】
 * 1. @SpringBootApplication：组合注解 = @Configuration + @EnableAutoConfiguration +
 *    @ComponentScan(默认扫描本类所在包 com.shanyue 及其子包，所以启动类必须放在最外层包)。
 * 2. @EnableTransactionManagement：开启注解式事务，Service 方法上加 @Transactional 即可
 *    （如 OrderServiceImpl.submit 下单两表写入需要事务保证）。
 * 3. @EnableCaching：开启 Spring Cache，C 端套餐列表 @Cacheable(cacheNames="setmealCache")
 *    才会生效（默认缓存在内存 ConcurrentMap，接 Redis 后自动切换为 Redis 存储）。
 * 4. @EnableAspectJAutoProxy：开启 AspectJ 自动代理（Boot 4 版本变化）——
 *    Spring Boot 4 模块化重构后不再提供 spring-boot-starter-aop，
 *    改为直接依赖 aspectjweaver（见 server pom）+ 在这里显式开启，
 *    AutoFillAspect 的 @Around/@Before 切面才会对 @AutoFill 注解方法生效。
 */
@EnableCaching
@EnableAspectJAutoProxy // Boot 4：显式开启 AOP 切面自动代理（配套 aspectjweaver 依赖）
@SpringBootApplication
@EnableTransactionManagement // 开启注解方式的事务管理
@Slf4j
public class ShanyueApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShanyueApplication.class, args);
        log.info("山月茶咖后端启动成功：http://localhost:8080");
        log.info("接口文档：http://localhost:8080/swagger-ui/index.html");
    }
}
