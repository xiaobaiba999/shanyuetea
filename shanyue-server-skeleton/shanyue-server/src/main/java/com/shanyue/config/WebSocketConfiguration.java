package com.shanyue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * 【本类知识点：WebSocket 服务端配置】
 * 1. WebSocket 是"全双工"长连接：握手一次后服务端可主动推送消息给浏览器/小程序，
 *    轮询（setInterval 定时请求）做不到实时且浪费。
 * 2. ServerEndpointExporter：扫描 @ServerEndpoint 注解并注册端点。
 *    注意：如果打 war 包部署到外部 Tomcat，这个 Bean 要删掉（外部容器自己会扫描）。
 * 3. 使用场景（课程同款）：来单提醒（type=1）、催单提醒（type=2）——
 *    管理端页面连接 /ws/{sid}，后端有新订单时主动推送，店家实时听到"您有新的外卖订单"。
 * 4. 骨架阶段只保证编译运行通过，业务推送见 websocket/WebSocketServer 中的 TODO。
 */
@Configuration
public class WebSocketConfiguration {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
