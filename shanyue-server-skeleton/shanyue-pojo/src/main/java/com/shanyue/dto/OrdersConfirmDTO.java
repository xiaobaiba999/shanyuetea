package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端接单参数。
 */
@Data
public class OrdersConfirmDTO implements Serializable {

    private Long id;

    /** 订单状态 1待付款 2待接单 3已接单 4派送中·待取餐 5已完成 6已取消 */
    private Integer status;
}
