package com.shanyue.exception;

import com.shanyue.constant.MessageConstant;
import com.shanyue.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 【本类知识点：全局异常处理器 @RestControllerAdvice】
 *
 * 一、作用：把散落在各处的 try-catch 收敛到一个类。Service 层只管 throw，这里统一"接住"，
 *          把异常翻译成前端能直接用的 Result JSON（code=0 + msg）。
 *
 * 二、匹配规则（面试高频）：
 *   多个 @ExceptionHandler 方法同时存在时，Spring 按"异常继承树就近匹配"——
 *   抛出 UserNotLoginException 时，同时命中 UserNotLoginException 和 BaseException 两个 handler，
 *   但取类层次更"近"的那个（UserNotLoginException 精确匹配优先），所以 401 不会被兜底 handler 抢走。
 *   文件里方法的书写顺序不影响匹配结果。
 *
 * 三、【教学重点：HTTP 401 与 Result code 的"双通道"约定】
 *   本工程对"失败"的表达有两条通道：
 *     通道 1（业务失败）：HTTP 200 + Result{code=0, msg="xx"} —— 密码错误、订单状态错误等，
 *        属于"请求正常处理了，但业务规则不允许"，前端 toast 展示 msg 即可；
 *     通道 2（协议层失败）：HTTP 401 + Result{code=0, msg="未登录"} —— token 缺失/过期/非法，
 *        属于"请求不该被处理"，前端 request 工具统一拦截 401：清 token → 跳登录页。
 *   为什么 UserNotLoginException 要单独一个 handler 加 @ResponseStatus(HttpStatus.UNAUTHORIZED)？
 *   因为若走 BaseException 兜底，响应是 200 + code=0，前端无法区分"未登录"和"普通业务失败"，
 *   无法触发自动跳登录页的逻辑。
 *
 * 四、注意：@RestControllerAdvice 自带 @ResponseBody，返回的 Result 会被序列化为 JSON。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理 SQL 唯一键冲突（重复键 → DuplicateException 风格的 "xx 已存在" 提示）
     * 场景：插入员工/分类时撞了唯一索引（employee.idx_username / category 的 name 唯一键），
     *      MySQL 报错形如：Duplicate entry 'zhangsan' for key 'employee.idx_username'
     * 处理：按空格切分取第 2 段（即冲突的值）拼成友好提示返回。
     * 教学提示：更推荐在 Service 里"先查后插"主动抛 DuplicateException（语义更清晰、提示可控），
     *          数据库唯一索引作为最后一道防线兜底。
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public Result handleSQLIntegrityConstraintViolationException(SQLIntegrityConstraintViolationException ex) {
        //Duplicate entry 'zhangsan' for key 'employee.idx_username'
        String message = ex.getMessage();
        if (message != null && message.contains("Duplicate entry")) {
            String[] split = message.split(" ");
            String duplicateValue = split[2];
            String msg = duplicateValue + " 已存在"; // DuplicateException 同风格提示
            log.warn("唯一键冲突：{}", message);
            return Result.error(msg);
        }
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 处理 C 端未登录（token 缺失 / 过期 / 非法）
     * 场景：JwtTokenUserInterceptor 解析 token 失败时 throw new UserNotLoginException(...)。
     * @ResponseStatus(HttpStatus.UNAUTHORIZED) 把 HTTP 状态码设为 401（默认是 200），
     * 响应体仍是 Result JSON（code=0 + msg="未登录"）—— 即上面说的"双通道"。
     * 前端 request 工具统一判断 401 → 清除本地 token → 跳转登录页。
     */
    @ExceptionHandler(UserNotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result handleUserNotLoginException(UserNotLoginException ex) {
        log.warn("用户未登录：{}", ex.getMessage());
        return Result.error(MessageConstant.USER_NOT_LOGIN);
    }

    /**
     * 处理业务异常（业务层兜底）
     * 场景：所有继承 BaseException 的业务异常（账号不存在/密码错误/订单状态错误/删除受限/购物车异常...）。
     * 直接把异常 message 原样返回给前端展示 —— 所以抛异常时 message 要用 MessageConstant 常量，别写死字符串。
     */
    @ExceptionHandler(BaseException.class)
    public Result handleBaseException(BaseException ex) {
        log.error("业务异常：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理数字格式异常
     * 场景：代码里对字符串做 Long.parseLong / Integer.parseInt 等解析时格式非法。
     * 给出比 "未知错误" 更有指向性的提示，方便前端/使用者定位是参数传错了。
     */
    @ExceptionHandler(NumberFormatException.class)
    public Result handleNumberFormatException(NumberFormatException ex) {
        log.error("数字格式异常：{}", ex.getMessage());
        return Result.error("数字格式不正确，请检查请求参数");
    }

    /**
     * 兜底：捕获其它一切未知异常（NPE、数组越界、MyBatis 异常...）
     * 1. 必须返回 MessageConstant.UNKNOWN_ERROR，不要把 e.getMessage() 直接吐给前端：
     *    异常消息可能带 SQL 语句、表名、文件路径等敏感信息，属于安全隐患（面试讲点）；
     * 2. 服务端日志打印完整堆栈（log.error 第二个参数直接传 ex），排查问题靠日志不靠响应。
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception ex) {
        log.error("系统未知异常", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }
}
