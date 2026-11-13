package com.shanyue.exception;

/**
 * 场景：地址簿业务校验失败。例如：外送下单但地址簿 id 无效、地址不属于当前用户。
 */
public class AddressBookBusinessException extends BaseException {

    public AddressBookBusinessException(String msg) {
        super(msg);
    }
}
