package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 规格组 VO（山月 v2.0）：返回给前端规格弹窗的结构化数据。
 * 例：{ id:1, drinkId:2, name:"杯型", sort:1,
 *       options:[{id:1,name:"中杯",priceDelta:0,sort:1},{id:2,name:"大杯",priceDelta:3,sort:2}] }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 所属饮品 id */
    private Long drinkId;

    /** 规格组名称 */
    private String name;

    /** 排序 */
    private Integer sort;

    /** 该组下选项列表 */
    private List<SpecOptionVO> options = new ArrayList<>();
}
