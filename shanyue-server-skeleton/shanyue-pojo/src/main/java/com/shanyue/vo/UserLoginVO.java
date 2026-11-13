package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * C 端微信登录返回 VO。
 * 【山月 v2.0】新增 nickName / avatarUrl（均可为空）：
 *   小程序首页展示"欢迎您，山月客人"；本地模拟登录时 nickname 取库中种子数据。
 * token：C 端 JWT，之后每个请求放请求头 token 里（shanyue.jwt.user-token-name 配置）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO implements Serializable {

    /** 用户 id */
    private Long id;

    /** 微信唯一标识（jscode2session 用 code 换取） */
    private String openid;

    /** C 端令牌（请求头名 authentication） */
    private String token;

    /* ------------------ 山月 v2.0 新增 ------------------ */

    /** 昵称（可空） */
    private String nickName;

    /** 头像 URL（可空） */
    private String avatarUrl;
}
