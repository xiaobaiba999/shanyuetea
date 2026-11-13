package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端取消订单参数。
 */
@Data
public class OrdersCancelDTO implements Serializable {

    private Long id;

    /** 订单取消原因 */
    private String cancelReason;
}
