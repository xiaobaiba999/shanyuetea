package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 【本类知识点：entity / dto / vo 三层模型怎么分】
 * entity 对应数据库表字段，service/mapper 层流转；dto 接收前端请求参数；vo 是返回给前端的视图。
 * 注解速记：@Data 自动生成 getter/setter/equals/hashCode/toString；
 *          @Builder 链式构造（Employee.builder().id(1L).build()）；
 *          @NoArgsConstructor/@AllArgsConstructor @Builder 三个通常成对出现（Builder 需要全参构造）。
 * 实现 Serializable：对象可能被序列化（缓存/分布式传输），规范要求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String name;

    /** MD5 加密后的密码（存储与比对见 EmployeeServiceImpl） */
    private String password;

    private String phone;

    /** 性别 0 女 1 男 */
    private String sex;

    private String idNumber;

    /** 状态 0 禁用 1 启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;
}
