package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分类表实体。
 * 课程里 type=1 是"菜品分类"、type=2 是"套餐分类"；山月文案叫"饮品分类"，类名/表名保持不变。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 类型: 1 饮品分类 2 套餐分类 */
    private Integer type;

    /** 分类名称 */
    private String name;

    /** 排序 */
    private Integer sort;

    /** 分类状态 0 表示禁用 1 表示启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 创建人 */
    private Long createUser;

    /** 修改人 */
    private Long updateUser;
}
