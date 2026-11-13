package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 套餐分页查询参数（管理端）。
 */
@Data
public class SetmealPageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    /** 名称（模糊查询） */
    private String name;

    /** 分类 id */
    private Integer categoryId;

    /** 状态 0 表示禁用 1 表示启用 */
    private Integer status;
}
