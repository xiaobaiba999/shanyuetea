package com.shanyue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 员工登录参数（管理端）。@Schema 会展示在 swagger 接口文档的字段说明里。
 */
@Data
@Schema(description = "员工登录时传递的数据模型")
public class EmployeeLoginDTO implements Serializable {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;
}
