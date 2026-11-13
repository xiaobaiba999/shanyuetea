package com.shanyue.exception;

/**
 * 场景：订单模块业务校验失败。例如：购物车为空不能下单、订单不存在或不是本人订单、
 * 订单状态与操作不匹配（待付款订单不能派送）等。
 */
public class OrderBusinessException extends BaseException {

    public OrderBusinessException(String msg) {
        super(msg);
    }
}
