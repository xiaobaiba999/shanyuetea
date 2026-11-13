package com.shanyue.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 【本类知识点：RedisTemplate 序列化定制】
 * 1. Spring Boot 默认提供的 RedisTemplate，key/value 都用 JDK 序列化，
 *    存进 Redis 后是乱码般的字节数组，在 redis-cli 里 key 都认不出来。
 * 2. 这里把 key 的序列化器换成 StringRedisSerializer：key 以人类可读字符串存储
 *    （如 SHOP_STATUS、drink_16、shanyue:pickup:2026-09-26）。
 * 3. value 保留默认 JDK 序列化：存的是 Java 对象（如 List<DrinkVO>），取出时自动反序列化。
 *    代价是 value 在 redis-cli 里不可读——想可读可改用 GenericJackson2JsonRedisSerializer（进阶）。
 */
@Configuration
@Slf4j
public class RedisTemplateConfiguration {

    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("开始配置 RedisTemplate...");
        RedisTemplate redisTemplate = new RedisTemplate<>();
        // 设置 Redis 连接工厂（host/port 来自 yml 的 spring.redis 配置）
        redisTemplate.setConnectionFactory(connectionFactory);
        // 设置 key 的序列化器为字符串
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        return redisTemplate;
    }
}
