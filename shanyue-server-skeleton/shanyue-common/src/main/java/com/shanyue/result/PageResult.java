package com.shanyue.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 【本类知识点：分页结果封装】
 * PageHelper 查询返回的 Page 对象既是 List 又带分页信息，直接返回给前端不友好，
 * 这里抽成 total（总记录数）+ records（当前页数据）两个字段，前端分页组件直接消费。
 * 泛型用了原生 List 而不是 List<T>（课程同款），保持对照一致。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult implements Serializable {

    /** 总记录数 */
    private long total;

    /** 当前页数据集合 */
    private List records;
}
