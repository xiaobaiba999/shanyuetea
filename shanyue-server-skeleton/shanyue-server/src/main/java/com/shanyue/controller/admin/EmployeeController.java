package com.shanyue.controller.admin;

import com.shanyue.constant.JwtClaimsConstant;
import com.shanyue.context.BaseContext;
import com.shanyue.dto.EmployeeDTO;
import com.shanyue.dto.EmployeeLoginDTO;
import com.shanyue.dto.EmployeePageQueryDTO;
import com.shanyue.dto.PasswordEditDTO;
import com.shanyue.entity.Employee;
import com.shanyue.properties.JwtProperties;
import com.shanyue.result.PageResult;
import com.shanyue.result.Result;
import com.shanyue.service.EmployeeService;
import com.shanyue.utils.JwtUtil;
import com.shanyue.vo.EmployeeLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 【管理端：员工管理】（对照课程 EmployeeController，8 个接口）
 * ┌ POST   /admin/employee            新增员工
 * ├ POST   /admin/employee/login      登录（WebMvcConfiguration 对它放行，无需 token）
 * ├ POST   /admin/employee/logout     退出（JWT 无状态，前端删 token 即"退出"，后端只回成功）
 * ├ POST   /admin/employee/status/{status}?id=  启用/禁用
 * ├ GET    /admin/employee/page       分页查询
 * ├ GET    /admin/employee/{id}       按 id 查询（编辑回显）
 * ├ PUT    /admin/employee            修改员工
 * └ PUT    /admin/employee/editPassword  修改密码（Service 留白，教程 9.5 节练手作业）
 *
 * 【本类知识点：登录后发 JWT 的完整闭环】
 * login 三连校验在 Service（查用户→验密码→查状态），本类负责"发证"：
 * 把员工 id 塞进 claims → JwtUtil.createJWT(管理端密钥, TTL, claims) 生成 token →
 * 连同用户信息装进 EmployeeLoginVO 下发；之后每个请求带请求头 token，
 * 由 JwtTokenAdminInterceptor 验证并把 empId 放回 BaseContext —— 有发有验，闭环完成。
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Tag(name = "员工管理")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     */
    @PostMapping("/login")
    @Operation(summary = "员工登录")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        // 1、Service 三连校验（查不到/密码错/被禁用都直接抛业务异常 → 全局处理器回错误信息）
        Employee employee = employeeService.login(employeeLoginDTO);

        // 2、登录成功 → 生成 jwt 令牌（载荷里放员工 id；密钥与 TTL 都来自 yml 配置）
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        // 3、组装返回 VO（id/userName/name/token）——前端存 token，之后每个请求放请求头 token
        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();
        return Result.success(employeeLoginVO);
    }

    /**
     * 退出登录
     * JWT 是无状态的：服务端不存会话，"退出"= 前端删掉本地 token；
     * 要服务端强制失效需引入黑名单/短令牌+刷新令牌机制（教学进阶，面试可讲）。
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<String> logout() {
        return Result.success();
    }

    /**
     * 新增员工
     */
    @PostMapping
    @Operation(summary = "新增员工")
    public Result save(@RequestBody EmployeeDTO employeeDTO) {
        log.info("新增员工：{}", employeeDTO);
        employeeService.save(employeeDTO);
        return Result.success();
    }

    /**
     * 分页查询员工
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询员工")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO) {
        log.info("分页查询员工：{}", employeePageQueryDTO);
        PageResult pageResult = employeeService.page(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 启用/禁用员工（status 走路径参数，id 走 query 参数——课程同款约定）
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "停用/启用员工")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("停用/启用员工：status={}，id={}", status, id);
        employeeService.startOrStop(status, id);
        return Result.success();
    }

    /**
     * 根据 id 查询员工（编辑页回显，密码已由 Service 置 "*"）
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据id查询员工")
    public Result<Employee> getById(@PathVariable Long id) {
        log.info("根据id查询员工：id={}", id);
        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }

    /**
     * 修改员工
     */
    @PutMapping
    @Operation(summary = "更新员工")
    public Result update(@RequestBody EmployeeDTO employeeDTO) {
        log.info("更新员工：{}", employeeDTO);
        employeeService.update(employeeDTO);
        return Result.success();
    }

    /**
     * 修改密码（教程 9.5 节练手作业：Service 留白，业务逻辑由你补全）
     *
     * 【Controller 已就位的两件事】
     * 1. 契约：PUT /admin/employee/editPassword，请求体 PasswordEditDTO（oldPassword/newPassword），
     *    返回 Result<String>——管理端前端 v1.5 的"修改密码"弹窗就是按这个契约调用的；
     * 2. 安全铁律"改谁的密码后端说了算"：empId 无条件用 BaseContext.getCurrentId() 覆盖，
     *    前端传什么都无效（防"改别人密码"，与 7.6 节"不信任前端 userId"同一思想）。
     * 剩下的三步业务（查库→旧密码比对→新密码更新）在 EmployeeServiceImpl.editPassword 等你补全。
     */
    @PutMapping("/editPassword")
    @Operation(summary = "修改密码")
    public Result<String> editPassword(@RequestBody PasswordEditDTO passwordEditDTO) {
        log.info("修改密码：{}", passwordEditDTO);
        // 不信任前端传来的 empId：统一覆盖为当前登录人（拦截器解析 token 后放进 ThreadLocal 的 id）
        passwordEditDTO.setEmpId(BaseContext.getCurrentId());
        employeeService.editPassword(passwordEditDTO);
        return Result.success();
    }
}
