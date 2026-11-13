package com.shanyue.service;

import com.shanyue.dto.EmployeeDTO;
import com.shanyue.dto.EmployeeLoginDTO;
import com.shanyue.dto.EmployeePageQueryDTO;
import com.shanyue.dto.PasswordEditDTO;
import com.shanyue.entity.Employee;
import com.shanyue.result.PageResult;

/**
 * 员工服务接口（对照课程 EmployeeService，方法签名 1:1）
 */
public interface EmployeeService {

    /**
     * 员工登录：校验用户名/密码/账号状态
     *
     * @param employeeLoginDTO username + password（明文，由 Controller 传入）
     * @return 员工实体（Controller 据此生成 JWT）
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    /**
     * 新增员工（默认密码 123456，MD5 后入库）
     *
     * @param employeeDTO 员工资料
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 分页查询员工
     *
     * @param employeePageQueryDTO page/pageSize/name
     * @return 分页结果
     */
    PageResult page(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启用/禁用员工账号
     *
     * @param status 0 禁用 1 启用
     * @param id     员工 id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据 id 查询员工（编辑页回显，密码置 * 防泄露）
     *
     * @param id 员工 id
     * @return 员工实体
     */
    Employee getById(Long id);

    /**
     * 更新员工资料
     *
     * @param employeeDTO 员工资料（含 id）
     */
    void update(EmployeeDTO employeeDTO);

    /**
     * 修改密码（教程 9.5 节练手作业：实现留白，在 EmployeeServiceImpl 补全）
     *
     * @param passwordEditDTO oldPassword/newPassword（empId 已由 Controller 用 BaseContext 覆盖为当前登录人）
     */
    void editPassword(PasswordEditDTO passwordEditDTO);
}
