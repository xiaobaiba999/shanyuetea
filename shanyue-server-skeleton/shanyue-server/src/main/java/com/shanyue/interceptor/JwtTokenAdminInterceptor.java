package com.shanyue.interceptor;

import com.shanyue.constant.JwtClaimsConstant;
import com.shanyue.constant.MessageConstant;
import com.shanyue.context.BaseContext;
import com.shanyue.exception.UserNotLoginException;
import com.shanyue.properties.JwtProperties;
import com.shanyue.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 【本类知识点：JWT 登录校验拦截器（管理端）—— 全项目请求的"安检门"】
 *
 * 一、拦截器是什么？
 *   HandlerInterceptor 是 Spring MVC 提供的"请求前置处理"钩子：请求到达 Controller 之前，
 *   先经过 preHandle()。返回 true 放行、false 拦截。登录校验这类"几乎所有接口都要做"的
 *   横切逻辑，放拦截器里写一次即可，Controller 零侵入。
 *
 * 二、本拦截器的职责（三步）：
 *   1. 从请求头取出 token（请求头名来自配置 shanyue.jwt.admin-token-name，默认都是 token）；
 *   2. 用管理端密钥 JwtUtil.parseJWT(adminSecretKey, token) 验签+解析：
 *      签名被篡改 / token 过期 / 格式非法 都会抛异常 → 统一走 catch；
 *   3. 解析成功 → 从 claims 里取出员工 id（empId）放进 ThreadLocal（BaseContext），
 *      之后整个请求链路里 Service/切面 用 BaseContext.getCurrentId() 就能拿到"当前是谁"。
 *
 * 三、失败处理（山月与课程的差异点，面试可讲）：
 *   课程原版在 catch 里直接 response.setStatus(401) 后 return false —— 只有一个状态码，
 *   前端拿不到错误信息；山月改为 throw new UserNotLoginException(未登录)，
 *   交给全局异常处理器 GlobalExceptionHandler 统一处理：
 *   @ResponseStatus(HttpStatus.UNAUTHORIZED) → HTTP 401 + Result{code=0,msg="未登录"}，
 *   "状态码"与"消息体"双通道齐全，前端 request 工具拦截 401 即可自动跳登录页。
 *   （WebMvcConfiguration 已把本拦截器挂到 /admin/**，放行 /admin/employee/login。）
 *
 * 四、afterCompletion 清理 ThreadLocal（必做！）：
 *   Tomcat 用线程池复用线程，处理完请求若不 remove 下个请求可能读到上个用户的 id（串号事故）。
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 校验 jwt（请求进 Controller 前执行）
     *
     * @param request  请求对象（取请求头用）
     * @param response 响应对象（本类不再直接操作它，401 由全局异常处理器负责）
     * @param handler  被拦截的"目标"：Controller 方法是 HandlerMethod，静态资源则不是
     * @return true 放行 / false 拦截（抛异常同样等于不放行）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断当前拦截到的是 Controller 的方法还是其他资源（如静态资源/swagger）
        // 不是 Controller 方法（HandlerMethod）就没有登录与否的语义，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 1、从请求头中获取令牌（请求头名读配置，与前端约定一致；管理端与 C 端可以不同名）
        String token = request.getHeader(jwtProperties.getAdminTokenName());
        log.info("管理端 jwt 校验，token={}", token);

        // 2、校验令牌：parseJWT 内部做"验签 + 过期检查"，任何一步失败都会抛异常
        try {
            // 用管理端专用密钥解析（密钥隔离：管理端 token 拿到 C 端解析会直接失败）
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);

            // 3、解析成功：从载荷 claims 里取出登录时放入的员工 id（Object → String → Long）
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());

            // 4、把员工 id 存入线程本地存储 ThreadLocal，本请求后续任何代码都能直接取
            BaseContext.setCurrentId(empId);
            log.info("当前员工 id：{}", empId);

            // 5、校验通过，放行（进入 Controller）
            return true;
        } catch (Exception ex) {
            // 6、不通过：token 缺失 / 篡改 / 过期 —— 抛业务异常，
            //    由 GlobalExceptionHandler 转成 HTTP 401 + Result{code=0, msg="未登录"}
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
    }

    /**
     * 请求完成后（视图渲染完/异常处理后）回调：清理 ThreadLocal
     * 必须清理！线程池复用线程，残留的 empId 会让下一个请求"顶替"上一个人的身份。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        BaseContext.removeCurrentId();
        log.debug("已清除当前员工 id（ThreadLocal 清理，防线程池串号）");
    }
}
