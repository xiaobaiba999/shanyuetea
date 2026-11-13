package com.shanyue.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单状态数量统计 VO（管理端订单列表页顶部三个数字）。
 */
@Data
public class OrderStatisticsVO implements Serializable {

    /** 待接单数量（状态 2） */
    private Integer toBeConfirmed;

    /** 已接单/待派送数量（状态 3） */
    private Integer confirmed;

    /** 派送中数量（状态 4，外送单；自取单为待取餐） */
    private Integer deliveryInProgress;
}
