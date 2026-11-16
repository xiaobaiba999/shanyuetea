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
 * 【本类知识点：JWT 登录校验拦截器（C 端小程序用户）—— 与管理端拦截器同构】
 *
 * 一、为什么两端要两个拦截器？
 *   管理端与 C 端的 JWT 用不同密钥签发（shanyue.jwt.admin-secret-key / user-secret-key），
 *   请求头虽然都叫 token，但"员工的令牌"与"用户的令牌"互不通用 ——
 *   拦截器分开写、路径分开挂（/admin/** 与 /user/**），实现"密钥隔离"，
 *   即使一端泄露也伪造不出另一端的合法令牌（安全设计，面试可讲）。
 *
 * 二、执行流程（与管理端逐行同构，差异只有三处）：
 *   1. 请求头名取 jwtProperties.getUserTokenName()；
 *   2. 解析密钥用 jwtProperties.getUserSecretKey()；
 *   3. claims 里取的是 userId（JwtClaimsConstant.USER_ID）。
 *
 * 三、放行路径（在 WebMvcConfiguration 里配置）：
 *   /user/** 全拦截，但放行 /user/user/login（登录前没有 token）
 *   与 /user/shop/status（进首页就要看"营业中/打烊"，未登录也允许读）。
 *
 * 四、失败处理：抛 UserNotLoginException → 全局异常处理器 → HTTP 401 + msg="未登录"
 *   （与管理端不同：C 端前端工具对 401 的处理是"清 token → 重新拉起登录"）。
 */
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 校验 jwt（请求进 Controller 前执行）
     *
     * @param request  请求对象（取请求头用）
     * @param response 响应对象（401 响应由全局异常处理器负责）
     * @param handler  被拦截目标：Controller 方法是 HandlerMethod，静态资源则不是
     * @return true 放行 / false 拦截（抛异常同样等于不放行）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 拦截到的不是 Controller 方法（如静态资源），无登录语义，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 1、从请求头中获取令牌（请求头名来自 shanyue.jwt.user-token-name 配置）
        String token = request.getHeader(jwtProperties.getUserTokenName());
        log.info("C 端 jwt 校验，token={}", token);

        // 2、校验令牌：验签 + 过期检查，失败抛异常走 catch
        try {
            // 用 C 端专用密钥解析（与管理端密钥隔离）
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);

            // 3、解析成功：取出登录时放入的用户 id（Object → String → Long）
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());

            // 4、用户 id 存入 ThreadLocal —— 后续 Service 里 BaseContext.getCurrentId()
            //    就是"当前下单/查购物车的这个用户"，一律不信任前端传来的 userId（防越权）
            BaseContext.setCurrentId(userId);
            log.info("当前用户 id：{}", userId);

            // 5、校验通过，放行
            return true;
        } catch (Exception ex) {
            // 6、不通过：token 缺失 / 非法 / 过期 → HTTP 401 + Result{code=0, msg="未登录"}
            //    前端 request 工具统一拦截 401：清本地 token → 跳转登录页
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
    }

    /**
     * 请求完成后清理 ThreadLocal（线程池复用，不清理会串号——与管理端同理）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        BaseContext.removeCurrentId();
        log.debug("已清除当前用户 id（ThreadLocal 清理，防线程池串号）");
    }
}
