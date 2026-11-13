package com.shanyue.exception;

/**
 * 场景：唯一键冲突（如新增员工时用户名已存在）。
 * 两种触发路径：
 *   1. 数据库唯一索引抛 SQLIntegrityConstraintViolationException，
 *      全局异常处理器识别 "Duplicate entry" 后转换为本异常的语义（"xx 已存在"）；
 *   2. 业务代码主动查库判断后抛出（先查后插能给出更友好的提示）。
 */
public class DuplicateException extends BaseException {

    public DuplicateException() {
    }

    public DuplicateException(String msg) {
        super(msg);
    }
}
