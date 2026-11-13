package com.shanyue.mapper;

import com.github.pagehelper.Page;
import com.shanyue.annotation.AutoFill;
import com.shanyue.dto.EmployeePageQueryDTO;
import com.shanyue.entity.Employee;
import com.shanyue.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 【本类知识点：员工 Mapper——接口零 SQL，全部语句在 XML】
 * 1. @Mapper：MyBatis 启动时为接口生成动态代理实现（无需写实现类），并注册进 Spring 容器。
 *    （另一种做法是启动类加 @MapperScan("com.shanyue.mapper") 统一扫描——两种等价，本工程用 @Mapper。）
 * 2. 5 个方法对应的 SQL 全部在 src/main/resources/mapper/EmployeeMapper.xml
 *    （page/update 是动态 SQL；getByUsername/insert/getById 是简单 SQL，也统一放 XML）。
 * 3. 字段名映射：数据库列 create_time ↔ 实体属性 createTime，依赖 yml 里
 *    mybatis.configuration.map-underscore-to-camel-case: true（驼峰映射，全局生效）。
 * 4. @AutoFill(INSERT/UPDATE)：贴在 insert/update 上，AutoFillAspect 会在执行前把
 *    create_time/update_time/create_user/update_user 四个公共字段填进实体参数。
 */
@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工（登录用）
     *
     * @param username 用户名（唯一索引 idx_username）
     * @return 员工实体，不存在返回 null
     */
    Employee getByUsername(String username);

    /**
     * 新增员工（XML：公共字段由 AutoFillAspect 预先填好）
     *
     * @param employee 由 AutoFillAspect 预先填好公共字段的实体
     */
    @AutoFill(OperationType.INSERT)
    void insert(Employee employee);

    /**
     * 分页查询员工（XML：name 模糊匹配可选；配合 PageHelper.startPage 使用）
     * 返回 Page：PageHelper.startPage(...) 后紧跟的第一条查询会被自动拼上 limit，
     * 返回值实际类型是 Page（既是 List 又带 total/总页数），Service 里转成 PageResult。
     *
     * @param employeePageQueryDTO page/pageSize/name
     * @return 分页结果
     */
    Page<Employee> page(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 更新员工（XML：动态 set 只更新非空字段）
     * 所以 startOrStop（只改 status）和 update（改资料）可以共用这一个方法。
     *
     * @param employee 只装需要改的字段 + id，公共字段由切面填充
     */
    @AutoFill(OperationType.UPDATE)
    void update(Employee employee);

    /**
     * 根据 id 查询员工（编辑页回显）
     *
     * @param id 员工 id
     * @return 员工实体
     */
    Employee getById(Long id);
}
