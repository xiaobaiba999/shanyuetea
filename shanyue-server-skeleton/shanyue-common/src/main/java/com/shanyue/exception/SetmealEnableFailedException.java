package com.shanyue.exception;

/**
 * 场景：套餐启用失败。套餐起售前必须校验其包含的所有饮品都处于起售状态，
 * 否则 C 端会出现"点了套餐但单品已停售"的矛盾。
 */
public class SetmealEnableFailedException extends BaseException {

    public SetmealEnableFailedException() {
    }

    public SetmealEnableFailedException(String msg) {
        super(msg);
    }
}
