package com.shanyue.constant;

/**
 * 【本类知识点：提示语统一管理】
 * 1. 提示文案写在常量类里而不是散落在代码中，好处：改文案只改一处、便于统一风格、防止错别字。
 * 2. Service 抛业务异常时传的 message 就是这里的常量，全局异常处理器会把它包装成 Result 返回给前端。
 */
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ALREADY_EXIST = "账号已存在";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String UNKNOWN_ERROR = "未知错误";
    /**
     * 未登录（管理端/用户端拦截器通用）。
     * 与 GlobalExceptionHandler 的 UserNotLoginException(401) 配套：
     * 拦截器解析 token 失败时 throw new UserNotLoginException(USER_NOT_LOGIN)。
     */
    public static final String USER_NOT_LOGIN = "未登录";
    public static final String CATEGORY_BE_RELATED_BY_SETMEAL = "当前分类关联了套餐,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DRINK = "当前分类关联了饮品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    /**
     * 地址为空，不能下单（外送模式下未选择/不存在有效地址簿时抛 OrderBusinessException）。
     */
    public static final String ADDRESS_BOOK_IS_NULL = "地址为空，不能下单";
    /**
     * 门店已打烊（山月新增）：ShopController 查询 Redis 营业状态=0 时，
     * 下单/加购前用该提示拦截，前端 toast 直接展示。
     */
    public static final String SHOP_CLOSE = "门店已打烊，暂时无法下单";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String SETMEAL_ENABLE_FAILED = "套餐内包含未启售饮品，无法启售";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String DRINK_ON_SALE = "起售中的饮品不能删除";
    public static final String SETMEAL_ON_SALE = "起售中的套餐不能删除";
    public static final String DRINK_BE_RELATED_BY_SETMEAL = "当前饮品关联了套餐,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String ORDER_ALREADY_PAID = "订单已支付，请勿重复支付";

    /** 下单模式非法（必须是 1 门店自取 / 2 外送） */
    public static final String DELIVERY_MODE_ERROR = "下单模式错误，仅支持 1 门店自取 / 2 外送";
}
