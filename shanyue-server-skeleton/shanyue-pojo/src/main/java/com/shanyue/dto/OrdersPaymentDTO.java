package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * C 端订单支付参数（苍穹原版保留）。
 * 课程真实实现会拿它去微信下单生成预支付交易单；山月骨架的 payment 接口做本地模拟。
 */
@Data
public class OrdersPaymentDTO implements Serializable {

    /** 订单号 */
    private String orderNumber;

    /** 付款方式 1 微信 2 支付宝 */
    private Integer payMethod;
}
