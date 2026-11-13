package com.shanyue.config;

import com.shanyue.interceptor.JwtTokenAdminInterceptor;
import com.shanyue.interceptor.JwtTokenUserInterceptor;
import com.shanyue.properties.LocalStorageProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 【本类知识点：Web 层配置类 WebMvcConfigurer】
 * 实现 WebMvcConfigurer 接口可以"扩展"Spring MVC 的默认行为（而不是重写覆盖）：
 *   1. addInterceptors：注册 JWT 拦截器（双端认证的核心）；
 *   2. addResourceHandlers：把 /upload/** 映射到本地磁盘目录（替代 OSS 的静态资源访问）；
 *   3. springdoc 接口文档 Bean：启动后访问 /swagger-ui/index.html。
 * 日期格式转换的 extendMessageConverters 在 JacksonObjectMapperConfig 里单独配置，避免职责混杂。
 */
@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    @Autowired
    private LocalStorageProperties localStorageProperties;

    /**
     * 注册自定义拦截器
     *
     * 【路径语义讲解】
     * - 管理端：拦截 /admin/** 全部接口，只放行 /admin/employee/login（不登录怎么拿 token？）
     * - C 端：拦截 /user/** 全部接口，放行两类：
     *      /user/user/login   —— 微信登录接口，登录前没有 token
     *      /user/shop/status  —— 店铺营业状态，进入小程序首页就要读，未登录也要能看到"营业中/打烊"
     * - 拦截器内部负责：解析 token → 失败抛 UserNotLoginException(401)；成功把 id 放进 BaseContext
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");

        // 管理端拦截器：校验员工 token
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/employee/login");

        // C 端拦截器：校验用户 token
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns("/user/user/login")
                .excludePathPatterns("/user/shop/status");
    }

    /**
     * 静态资源映射：/upload/** → 本地磁盘目录（shanyue.local-storage.path 配置项）
     * 例如上传文件保存到 D:/shanyue/upload/abc.png，
     * 浏览器访问 http://localhost:8080/upload/abc.png 即可查看（等价于课程里 OSS 的公开 URL）。
     * 注意目录末尾必须带 / ，否则 Spring 会当作文件名前缀拼接失败。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String path = localStorageProperties.getPath();
        if (path != null && !path.endsWith("/") && !path.endsWith("\\")) {
            path = path + "/";
        }
        log.info("注册本地静态资源映射：/upload/** → file:{} ", path);
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + path);
    }

    /**
     * 接口文档基本信息（springdoc）：访问 http://localhost:8080/swagger-ui/index.html
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("山月茶咖项目接口文档")
                .version("2.0")
                .description("山月茶咖扫码点单系统 —— 管理端 + C端小程序接口"));
    }

    /**
     * 管理端接口分组：文档页右上角下拉可切换"管理端接口 / 用户端接口"
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("管理端接口")
                .pathsToMatch("/admin/**")
                .build();
    }

    /**
     * 用户端接口分组
     */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("用户端接口")
                .pathsToMatch("/user/**")
                .build();
    }
}
