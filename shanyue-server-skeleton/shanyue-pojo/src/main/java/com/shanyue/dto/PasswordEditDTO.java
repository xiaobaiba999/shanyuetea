package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码参数（管理端，课程同款保留）。
 */
@Data
public class PasswordEditDTO implements Serializable {

    /** 员工 id */
    private Long empId;

    /** 旧密码 */
    private String oldPassword;

    /** 新密码 */
    private String newPassword;
}
