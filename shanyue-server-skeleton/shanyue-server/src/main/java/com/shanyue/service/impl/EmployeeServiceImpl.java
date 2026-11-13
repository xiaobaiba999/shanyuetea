package com.shanyue.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.shanyue.constant.MessageConstant;
import com.shanyue.constant.PasswordConstant;
import com.shanyue.constant.StatusConstant;
import com.shanyue.context.BaseContext;
import com.shanyue.dto.EmployeeDTO;
import com.shanyue.dto.EmployeeLoginDTO;
import com.shanyue.dto.EmployeePageQueryDTO;
import com.shanyue.dto.PasswordEditDTO;
import com.shanyue.entity.Employee;
import com.shanyue.exception.AccountLockedException;
import com.shanyue.exception.AccountNotFoundException;
import com.shanyue.exception.PasswordErrorException;
import com.shanyue.mapper.EmployeeMapper;
import com.shanyue.result.PageResult;
import com.shanyue.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.List;

/**
 * 【本类知识点：员工服务实现（完整实现，对照课程 EmployeeServiceImpl）】
 * 一、登录三连校验（顺序有讲究）：
 *   1. 按用户名查库 → 查不到抛 AccountNotFoundException（不提示"用户名不存在"还是"密码错误"的差异，
 *      生产上为了防撞库通常统一提示"用户名或密码错误"，课程为了教学拆成两个异常，面试可讲）；
 *   2. 明文密码 MD5 后与库中密文比对（DigestUtils.md5DigestAsHex 是 Spring 自带工具，32 位小写 hex）；
 *   3. status==DISABLE（0）抛 AccountLockedException —— 被禁用的账号不让登录。
 * 二、新增/更新员工：公共字段（create_time 等）不用手动 set，AutoFillAspect 在 Mapper 层统一填充；
 *    Service 里只补业务字段（默认密码/启用状态）。
 * 三、修改密码 editPassword：练手作业留白（教程 9.5 节）——Controller 已把 empId 覆盖为当前登录人，
 *    三步业务（查库→旧密码 MD5 比对→新密码更新）由你补全，实现步骤提示见方法注释。
 */
@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     */
    @Override
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        // 1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        // 2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            // 账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 密码比对：把前端传来的明文做 MD5 摘要后与库里密文比对（库中存的从来不是明文）
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(employee.getPassword())) {
            // 密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus().equals(StatusConstant.DISABLE)) {
            // 账号被锁定（被管理员禁用）
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        // 3、校验全部通过，返回实体对象（Controller 用它生成 JWT）
        return employee;
    }

    /**
     * 新增员工
     */
    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        // 同名字段批量拷贝（id/name/username/phone/sex/idNumber）
        BeanUtils.copyProperties(employeeDTO, employee);

        // 补业务默认值：初始密码 123456（MD5 后入库）、默认启用
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));
        employee.setStatus(StatusConstant.ENABLE);

        // create_time/update_time/create_user/update_user 由 AutoFillAspect 在 Mapper 前自动填充
        // （当前登录人 id 来自 BaseContext——拦截器解析 token 后放进 ThreadLocal 的）
        employeeMapper.insert(employee);
    }

    /**
     * 分页查询员工
     */
    @Override
    public PageResult page(EmployeePageQueryDTO employeePageQueryDTO) {
        // PageHelper.startPage 必须紧跟第一条查询，ThreadLocal 方式把分页参数传给拦截器拼 limit
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());

        Page<Employee> page = employeeMapper.page(employeePageQueryDTO);
        long total = page.getTotal();
        List<Employee> records = page.getResult();
        return new PageResult(total, records);
    }

    /**
     * 启用/禁用员工
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        // 只装 id + status，配合动态 set 的 update，天然"只改状态"
        Employee employee = Employee.builder()
                .id(id)
                .status(status)
                .build();
        employeeMapper.update(employee);
    }

    /**
     * 根据 id 查询员工
     */
    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        // 教学细节：密码不能回显给前端（安全），置空即可
        employee.setPassword("*");
        return employee;
    }

    /**
     * 更新员工
     */
    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        // 公共字段 update_time/update_user 由切面填充（BaseContext 取当前登录人）
        employeeMapper.update(employee);
    }

    /**
     * TODO 修改密码（教程 9.5 节练手作业：留白，由你补全）
     * 实现步骤提示（弹药全在第 8/9 章领过，不用学新东西）：
     *   1. employeeMapper.getById(passwordEditDTO.getEmpId()) 查出当前员工（拿到库里存的旧密码密文）；
     *      查不到先抛 AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND) —— "null 最先判"是第 8 章铁律；
     *   2. 旧密码明文 DigestUtils.md5DigestAsHex(passwordEditDTO.getOldPassword().getBytes()) 转 MD5，
     *      与库里密文 employee.getPassword() 比对，不一致抛
     *      PasswordEditFailedException(MessageConstant.PASSWORD_EDIT_FAILED)（第 5 章的备用异常，此刻派上用场）；
     *   3. 新密码转 MD5，Employee.builder().id(...).password(...).build() 只装两个字段，
     *      复用 employeeMapper.update(...) 落库 —— 动态 <set> 只会更新 password 一列（9.3 节已就位）。
     *
     * @param passwordEditDTO empId/oldPassword/newPassword（empId 已被 Controller 用 BaseContext 覆盖）
     */
    @Override
    public void editPassword(PasswordEditDTO passwordEditDTO) {
        // TODO 练手作业（教程 9.5 节）：按上面三步提示实现；写完对照答案 txt 第 9.5 节
        log.warn("修改密码尚未实现（教程 9.5 节练手作业）——当前调用是静默失败，密码不会真的变更");
    }
}
