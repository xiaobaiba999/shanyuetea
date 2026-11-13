package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 饮品总览 VO（工作台"饮品管理"卡片：起售/停售数量）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishOverViewVO implements Serializable {

    /** 已起售数量 */
    private Integer sold;

    /** 已停售数量 */
    private Integer discontinued;
}
