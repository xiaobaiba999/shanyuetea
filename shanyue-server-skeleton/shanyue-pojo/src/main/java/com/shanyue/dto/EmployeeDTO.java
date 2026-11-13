package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 新增/修改员工的参数模型（管理端）。密码不在其中：新增给默认密码，修改走单独接口。
 */
@Data
public class EmployeeDTO implements Serializable {

    private Long id;

    private String username;

    private String name;

    private String phone;

    private String sex;

    private String idNumber;
}
