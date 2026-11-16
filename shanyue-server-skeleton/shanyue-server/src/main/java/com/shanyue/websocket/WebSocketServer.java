package com.shanyue.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 【本类知识点：WebSocket 服务端点（骨架版）】
 *
 * 一、@ServerEndpoint("/ws/{sid}")：声明 WS 端点路径，{sid} 是路径参数（一般放客户端标识）。
 * 二、管理端页面（监工屏）连接后，后端可以随时"推"消息过去：
 *      来单提醒：订单支付成功后推送 {"type":1,"orderId":x,"content":"您有新的订单"}（type=1）
 *      催单提醒：用户催单时推送 {"type":2,"orderId":x,"content":"用户催单"}（type=2）
 *    前端 onmessage 里按 type 播放不同提示音/弹窗。
 * 三、并发容器：多个客户端同时连接，sessionMap 会被并发读写，必须用 ConcurrentHashMap。
 * 四、推送入口：OrderServiceImpl.paySuccess 与 reminder 里调用 sendToAllClient（与课程工程方法名一致）。
 */
@Component
@Slf4j
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    /** 存放所有在线客户端：sid → 会话。课程演示用 HashMap，线程不安全，山月直接用 ConcurrentHashMap */
    private static final Map<String, Session> sessionMap = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 连接建立成功时调用
     *
     * @param session 会话对象（给某个客户端发消息用它）
     * @param sid     路径参数：客户端标识
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("WebSocket 客户端连接：sid={}", sid);
        sessionMap.put(sid, session);
    }

    /**
     * 收到客户端消息时调用
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到来自 sid={} 的消息：{}", sid, message);
        // TODO 心跳保活：客户端定时发 "ping"，服务端回 "pong"，避免连接被网络设备断开
    }

    /**
     * 连接关闭时调用
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("WebSocket 客户端断开：sid={}", sid);
        sessionMap.remove(sid);
    }

    /**
     * 连接发生错误时调用
     */
    @OnError
    public void onError(Throwable error) {
        log.error("WebSocket 发生错误", error);
    }

    /**
     * 群发消息给所有在线客户端（来单提醒 / 催单提醒的推送入口）
     * 方法名与课程工程一致（sendToAllClient），OrderServiceImpl 的支付成功/催单处已调用本方法
     */
    public void sendToAllClient(String message) {
        Collection<Session> sessions = sessionMap.values();
        for (Session session : sessions) {
            try {
                // 基本文本消息推送；JSON 格式与 type 约定见类注释
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("WebSocket 推送消息失败", e);
            }
        }
    }
}
