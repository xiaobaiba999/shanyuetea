package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 分类分页查询参数（管理端）。
 * GET 查询参数自动绑定：/admin/category/page?page=1&pageSize=10&name=咖啡&type=1
 * int 类型缺省为 0，Integer 缺省为 null —— 分页参数这里用 int（course 同款）。
 */
@Data
public class CategoryPageQueryDTO implements Serializable {

    /** 页码 */
    private int page;

    /** 每页记录数 */
    private int pageSize;

    /** 分类名称（模糊查询，可空） */
    private String name;

    /** 分类类型 1 饮品分类 2 套餐分类（可空） */
    private Integer type;
}
