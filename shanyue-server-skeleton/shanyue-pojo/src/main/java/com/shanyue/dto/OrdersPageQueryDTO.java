package com.shanyue.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单分页查询参数（管理端 conditionSearch / C 端 historyOrders 共用）。
 * 【山月 v2.0】新增 deliveryMode：管理端可按"自取/外送"筛选订单。
 * @DateTimeFormat：GET 请求的字符串参数 → LocalDateTime 的转换格式（JSON 请求体走的是 @JsonFormat，两者不同）。
 */
@Data
public class OrdersPageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    /** 订单号（模糊查询） */
    private String number;

    /** 手机号（模糊查询） */
    private String phone;

    /** 订单状态 1待付款 2待接单 3已接单 4派送中·待取餐 5已完成 6已取消 */
    private Integer status;

    /** 下单模式（山月 v2.0）1 门店自取 2 外送；不传查全部 */
    private Integer deliveryMode;

    /** 下单时间起（yyyy-MM-dd HH:mm:ss） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    /** 下单时间止（yyyy-MM-dd HH:mm:ss） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 下单用户 id（C 端历史订单时由服务端从 BaseContext 填入） */
    private Long userId;
}
