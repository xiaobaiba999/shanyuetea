package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 口味实体（课程对照保留）。
 * 山月 v2.0 用 SpecGroup + SpecOption（结构化规格组）替代了课程的 drink_flavor 口味表，
 * 本实体与 drink_flavor 表仅作对照/过渡兼容使用，业务代码不再读写。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrinkFlavor implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 饮品 id */
    private Long drinkId;

    /** 口味名称，如：辣度 */
    private String name;

    /** 口味可选值 JSON 数组字符串，如：["不辣","微辣","中辣"] */
    private String value;
}
