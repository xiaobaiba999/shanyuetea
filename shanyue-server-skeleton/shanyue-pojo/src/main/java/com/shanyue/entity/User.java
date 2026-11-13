package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C 端微信用户实体。对应表 user。
 * openid 是微信侧唯一标识：登录时用 wx.login 的 code 调 jscode2session 接口换取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 微信用户唯一标识 */
    private String openid;

    /** 昵称（山月 v2.0：UserLoginVO.nickName，可空） */
    private String nickName;

    /** 姓名（课程同款字段保留） */
    private String name;

    /** 手机号 */
    private String phone;

    /** 性别 0 女 1 男 */
    private String sex;

    /** 身份证号 */
    private String idNumber;

    /** 头像 URL */
    private String avatar;

    /** 注册时间 */
    private LocalDateTime createTime;
}
