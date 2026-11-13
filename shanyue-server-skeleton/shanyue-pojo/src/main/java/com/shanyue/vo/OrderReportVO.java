package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单统计报表 VO（管理端报表页）。
 * 课程同款设计：日期/数值列表用逗号拼接的字符串传输（如 "2026-09-01,2026-09-02"），
 * 前端 ECharts 需要自行 split；更现代的做法是直接传数组。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReportVO implements Serializable {

    /** 日期，以逗号分隔，例如：2026-09-01,2026-09-02,2026-09-03 */
    private String dateList;

    /** 每日订单数，以逗号分隔，例如：260,210,215 */
    private String orderCountList;

    /** 每日有效订单数，以逗号分隔，例如：20,21,10 */
    private String validOrderCountList;

    /** 订单总数 */
    private Integer totalOrderCount;

    /** 有效订单数 */
    private Integer validOrderCount;

    /** 订单完成率 */
    private Double orderCompletionRate;
}
