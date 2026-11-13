package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 套餐总览 VO（工作台"套餐管理"卡片）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetmealOverViewVO implements Serializable {

    /** 已起售数量 */
    private Integer sold;

    /** 已停售数量 */
    private Integer discontinued;
}
