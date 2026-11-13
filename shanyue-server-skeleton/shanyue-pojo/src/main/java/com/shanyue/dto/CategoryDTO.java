package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 新增/修改分类的参数模型（管理端）。
 * 为什么不用 Category 实体直接接收？—— 实体里带 createTime/createUser 等由服务端维护的字段，
 * 让前端可传就会埋下越权隐患；dto 只暴露"前端允许传的字段"。
 */
@Data
public class CategoryDTO implements Serializable {

    /** 主键（修改时必传，新增时为空） */
    private Long id;

    /** 类型 1 饮品分类 2 套餐分类 */
    private Integer type;

    /** 分类名称 */
    private String name;

    /** 排序 */
    private Integer sort;
}
