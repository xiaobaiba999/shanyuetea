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
     * 扩展消息转换器：改造【默认的】Jackson 转换器，替换其 ObjectMapper 为统一日期格式版
     *
     * ⚠ 为什么不是"新 new 一个转换器 add(0, ...)"插到最前面？（山月踩坑记录）
     *   插到第 0 位会让它抢在 ByteArrayHttpMessageConverter 之前处理 byte[] 响应——
     *   springdoc 的 /v3/api-docs 接口返回的就是 byte[]，被 Jackson 抢到后会整体
     *   base64 编码成一个字符串，swagger-ui 页面打不开、Apifox 按 URL 导入也失败。
     *   改造默认转换器（保持默认顺序：ByteArray 在前、Jackson 在后）既统一了日期格式，
     *   又不影响 byte[]/String 走各自的专用转换器，一举两得。
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> c : converters) {
            if (c instanceof MappingJackson2HttpMessageConverter) {
                ((MappingJackson2HttpMessageConverter) c).setObjectMapper(buildObjectMapper());
                log.info("已为默认 Jackson 消息转换器设置统一日期格式（yyyy-MM-dd HH:mm:ss）...");
                return;
            }
        }
        // 兜底：万一默认转换器不存在（理论不会发生），再退回"插入到末尾"的方式
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(buildObjectMapper());
        converters.add(converter);
        log.info("注册自定义日期格式消息转换器（yyyy-MM-dd HH:mm:ss）...");
    }
}
