package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户统计报表 VO（管理端报表页）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReportVO implements Serializable {

    /** 日期，以逗号分隔，例如：2026-09-01,2026-09-02,2026-09-03 */
    private String dateList;

    /** 用户总量，以逗号分隔，例如：200,210,220 */
    private String totalUserList;

    /** 新增用户，以逗号分隔，例如：20,21,10 */
    private String newUserList;
}
