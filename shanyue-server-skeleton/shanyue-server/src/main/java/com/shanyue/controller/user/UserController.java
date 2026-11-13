package com.shanyue.controller.user;

import com.shanyue.dto.UserLoginDTO;
import com.shanyue.entity.User;
import com.shanyue.result.Result;
import com.shanyue.service.UserService;
import com.shanyue.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【C 端：用户】（对照课程 UserController，山月补了 profile）
 * ┌ POST /user/user/login   微信登录（code 调 jscode2session 换 openid）
 * └ GET  /user/user/profile 查询当前登录用户资料（"我的"页面）
 *
 * 【本类知识点：登录接口为什么不用校验 token？】
 * WebMvcConfiguration 对 /user/user/login 放行 —— 登录前用户根本没有 token，
 * 拦截器必须放过它，否则"先有鸡还是先有蛋"死循环。登录成功拿到 token 后，
 * 其余 /user/** 请求都会被 JwtTokenUserInterceptor 校验。
 *
 * 【token 放哪？】课程用请求头 authentication，山月统一为 token
 * （shanyue.jwt.user-token-name 配置），前端 request 工具统一携带。
 */
@RestController("userUserController")
@RequestMapping("/user/user")
@Tag(name = "C端-用户接口")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 微信登录
     *
     * @param userLoginDTO 小程序 wx.login() 拿到的 code
     * @return id/openid/token/nickName/avatarUrl
     */
    @PostMapping("/login")
    @Operation(summary = "C端-微信登录")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("C 端用户登录：{}", userLoginDTO);
        UserLoginVO userLoginVO = userService.login(userLoginDTO);
        return Result.success(userLoginVO);
    }

    /**
     * 查询当前登录用户资料（"我的"页面：昵称/头像/积分等展示）
     * id 不从前端传 —— 拦截器解析 token 后已把 userId 放进 ThreadLocal，
     * Service 直接取，天然防"看别人的资料"（防越权）。
     */
    @GetMapping("/profile")
    @Operation(summary = "C端-查询用户资料")
    public Result<User> profile() {
        log.info("查询当前用户资料");
        User user = userService.profile();
        return Result.success(user);
    }
}
