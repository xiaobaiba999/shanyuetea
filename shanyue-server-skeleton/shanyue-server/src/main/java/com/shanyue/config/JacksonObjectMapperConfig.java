package com.shanyue.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 【本类知识点：Jackson 3 日期格式统一（JsonMapperBuilderCustomizer）】
 *
 * 一、解决什么问题？
 *   Java 8 的 LocalDateTime 默认被 Jackson 序列化成 "2026-09-26T10:15:30"（带 T），
 *   前端一般期望 "2026-09-26 10:15:30"；反序列化同理，否则前端传时间直接报错。
 *
 * 二、Spring Boot 4 的 Jackson 变化（v2.1 升级口径）：
 *   1. Jackson 2 → Jackson 3：databind 包名从 com.fasterxml.jackson.databind 换成
 *      tools.jackson.databind（Jackson 3 把模块拆得更细）；注解包 com.fasterxml.jackson.annotation
 *      保持不变，所以 DTO 上的 @JsonFormat 一行不用改。
 *   2. Boot 4 的官方定制点从"改消息转换器里的 ObjectMapper"换成了
 *      JsonMapperBuilderCustomizer：向 Spring 容器注册一个该接口的 Bean，
 *      Boot 自动装配的 JsonMapper.Builder 会收集所有 customizer 逐个回调，
 *      最终 build 出全局唯一的 ObjectMapper —— @RequestBody 与响应 JSON 全走它。
 *   3. Jackson 3 的 ObjectMapper 是不可变对象（改配置必须经 builder 重建），
 *      这也是不再提供 setObjectMapper 这种"运行期改对象"姿势的原因。
 *   4. java.time 支持已内置（不再需要手动 new JavaTimeModule() 注册），
 *      这里注册 SimpleModule 的目的是"覆盖默认 ISO 格式"为我们业务统一的格式。
 *
 * 三、注意：这里管的是 @RequestBody（JSON 请求体）与响应 JSON；
 *   GET 请求的 URL 参数（如 ?beginTime=...）走的是 @DateTimeFormat（见 OrdersPageQueryDTO）。
 */
@Configuration
@Slf4j
public class JacksonObjectMapperConfig implements JsonMapperBuilderCustomizer {

    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    /**
     * 定制全局 JsonMapper：注册 java.time 三兄弟的统一出入参格式
     * （旧版"extendsMessageConverters 替换转换器内部 ObjectMapper"的写法在 Boot 4 已不适用，
     *  Jackson 3 的 ObjectMapper 不可变，一律通过 Builder 定制。）
     */
    @Override
    public void customize(tools.jackson.databind.json.JsonMapper.Builder builder) {
        SimpleModule javaTimeModule = new SimpleModule();

        // LocalDateTime ↔ "yyyy-MM-dd HH:mm:ss"
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT);
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));

        // LocalDate ↔ "yyyy-MM-dd"
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));

        // LocalTime ↔ "HH:mm:ss"
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT);
        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(timeFormatter));
        javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormatter));

        builder.addModule(javaTimeModule);
        log.info("Jackson 3 全局日期格式已定制（yyyy-MM-dd HH:mm:ss）...");
    }
}
