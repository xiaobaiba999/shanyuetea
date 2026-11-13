package com.shanyue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 员工登录返回 VO（管理端）。前端把 token 存起来，之后每个请求放到请求头 token 里。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "员工登录返回的数据格式")
public class EmployeeLoginVO implements Serializable {

    @Schema(description = "主键值")
    private Long id;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "jwt令牌")
    private String token;
}
