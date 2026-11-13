package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 规格组实体（山月核心 v2.0，替代课程的 DrinkFlavor 口味表）。
 * 一个饮品有多个规格组（杯型/温度/糖度），每个规格组下有多个选项（SpecOption）。
 * 对应表 spec_group。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 所属饮品 id */
    private Long drinkId;

    /** 规格组名称，如：杯型 / 温度 / 糖度 */
    private String name;

    /** 排序（弹窗内从上到下） */
    private Integer sort;
}
