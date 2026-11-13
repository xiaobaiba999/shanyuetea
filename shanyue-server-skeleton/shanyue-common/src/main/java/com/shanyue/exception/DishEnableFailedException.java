package com.shanyue.exception;

/**
 * 场景：饮品启售失败。与 SetmealEnableFailedException 对应——
 * 单个饮品起售本身没有前置条件，但批量起售/起售校验失败（例如菜品数据异常）时抛出；
 * 课程原版在套餐启售校验里若发现"套餐内包含停售饮品"，同样会借助本异常组语义提示。
 * 全局异常处理器按 BaseException 兜底：Result.error(异常消息)。
 */
public class DishEnableFailedException extends BaseException {

    public DishEnableFailedException() {
    }

    public DishEnableFailedException(String msg) {
        super(msg);
    }
}
