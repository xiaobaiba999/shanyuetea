package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端拒绝订单参数。
 */
@Data
public class OrdersRejectionDTO implements Serializable {

    private Long id;

    /** 订单拒绝原因 */
    private String rejectionReason;
}
