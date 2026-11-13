package com.shanyue.exception;

/**
 * 场景：购物车业务校验失败。例如：往购物车加一个已停售的饮品、购物车为空却想结算等。
 */
public class ShoppingCartBusinessException extends BaseException {

    public ShoppingCartBusinessException(String msg) {
        super(msg);
    }
}
