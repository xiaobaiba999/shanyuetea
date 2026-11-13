package com.shanyue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 【本类知识点：RestTemplate —— Spring 自带的 HTTP 客户端】
 * 1. 后端调用第三方 HTTP 接口（微信登录 jscode2session、支付下单、短信...）都用它。
 * 2. 交给 Spring 容器统一管理（@Bean），而不是每次 new：方便将来统一加超时/日志/拦截器。
 * 3. UserServiceImpl 真实登录就是用它发 jscode2session 请求（课程用自封装 HttpClientUtil，效果一样）；
 *    开启那块代码时无需额外依赖。
 */
@Configuration
public class RestTemplateConfiguration {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
