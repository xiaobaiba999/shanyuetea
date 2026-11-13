package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 规格组参数模型（山月 v2.0）。
 * 前端提交：{ name: "杯型", sort: 1, options: [{name:"中杯",priceDelta:0,sort:1}, ...] }
 * drinkId / groupId 由服务端在保存时回填，前端无需传。
 */
@Data
public class SpecGroupDTO implements Serializable {

    /** 规格组 id（修改时已有组可带 id，整体覆盖策略下可以不带） */
    private Long id;

    /** 所属饮品 id（服务端回填） */
    private Long drinkId;

    /** 规格组名称，如：杯型 */
    private String name;

    /** 排序 */
    private Integer sort;

    /** 该组下的选项列表 */
    private List<SpecOptionDTO> options = new ArrayList<>();
}
