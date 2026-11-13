package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 饮品表实体（课程里叫"菜品" Drink，山月不改类名，仅菜单文案叫"饮品"）。
 * 规格信息不在本表：规格组见 SpecGroup / SpecOption（山月 v2.0）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Drink implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 饮品名称 */
    private String name;

    /** 饮品分类 id */
    private Long categoryId;

    /** 饮品基础价格（最终售价 = 基础价 + 所选规格加价之和，见 ShoppingCartServiceImpl） */
    private BigDecimal price;

    /** 图片 */
    private String image;

    /** 描述信息 */
    private String description;

    /** 0 停售 1 起售 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;
}
