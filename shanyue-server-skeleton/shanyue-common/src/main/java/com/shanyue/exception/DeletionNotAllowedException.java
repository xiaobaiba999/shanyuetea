package com.shanyue.exception;

/**
 * 场景：删除被禁止。例如：起售中的饮品不能删、被套餐引用的饮品不能删、
 * 分类下还有饮品/套餐时分类不能删。
 */
public class DeletionNotAllowedException extends BaseException {

    public DeletionNotAllowedException(String msg) {
        super(msg);
    }
}
