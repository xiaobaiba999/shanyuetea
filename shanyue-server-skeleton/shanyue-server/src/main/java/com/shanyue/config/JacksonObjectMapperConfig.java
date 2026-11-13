package com.shanyue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 【本类知识点：Jackson 日期格式统一（消息转换器扩展）】
 *
 * 一、解决什么问题？
 *   Java 8 的 LocalDateTime 默认被 Jackson 序列化成 "2026-09-26T10:15:30"（带 T），
 *   前端一般期望 "2026-09-26 10:15:30"；反序列化同理，否则前端传时间直接报错。
 *
 * 二、原理：
 *   1. Spring MVC 用 HttpMessageConverter 做 Java 对象 ↔ JSON 的互转，JSON 转换器默认是
 *      MappingJackson2HttpMessageConverter（内部持有一个 ObjectMapper）；
 *   2. 定制 ObjectMapper：注册 JavaTimeModule，指定 LocalDateTime/LocalDate/LocalTime 的格式；
 *   3. extendMessageConverters：把自己的转换器插到列表第 0 位（add(0, ...)），
 *      Spring 按顺序匹配，排在前面的优先生效。
 *
 * 三、注意：这里管的是 @RequestBody（JSON 请求体）与响应 JSON；
 *   GET 请求的 URL 参数（如 ?beginTime=...）走的是 @DateTimeFormat（见 OrdersPageQueryDTO）。
 */
@Configuration
@Slf4j
public class JacksonObjectMapperConfig implements WebMvcConfigurer {

    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    /**
     * 构建统一日期格式的 ObjectMapper（供消息转换器使用）
     */
    public static ObjectMapper buildObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // JavaTimeModule 提供 LocalDateTime 等 java.time 类型的序列化支持
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // LocalDateTime ↔ "yyyy-MM-dd HH:mm:ss"
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)));

        // LocalDate ↔ "yyyy-MM-dd"
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)));
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)));

        // LocalTime ↔ "HH:mm:ss"
        javaTimeModule.addSerializer(LocalTime.class,
                new LocalTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)));
        javaTimeModule.addDeserializer(LocalTime.class,
                new LocalTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)));

        objectMapper.registerModule(javaTimeModule);
        // 关闭"把日期按时间戳数字输出"的默认行为
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    /**
     * 扩展消息转换器：把定制的 Jackson 转换器插到最前面，优先生效
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("注册自定义日期格式消息转换器（yyyy-MM-dd HH:mm:ss）...");
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        // 设置对象转换器：Java 对象 → JSON 使用这里的 ObjectMapper
        converter.setObjectMapper(buildObjectMapper());
        // 插入到容器第 0 位，最先执行
        converters.add(0, converter);
    }
}
