package com.shanyue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作台/报表统计的起止时间参数（课程同款保留）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataOverViewQueryDTO implements Serializable {

    private LocalDateTime begin;

    private LocalDateTime end;
}
